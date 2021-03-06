/*
 * This file is part of the BigConnect project.
 *
 * Copyright (c) 2013-2020 MWARE SOLUTIONS SRL
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * MWARE SOLUTIONS SRL, MWARE SOLUTIONS SRL DISCLAIMS THE WARRANTY OF
 * NON INFRINGEMENT OF THIRD PARTY RIGHTS

 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA, or download the license from the following URL:
 * https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the BigConnect software without
 * disclosing the source code of your own applications.
 *
 * These activities include: offering paid services to customers as an ASP,
 * embedding the product in a web application, shipping BigConnect with a
 * closed source product.
 */
package com.mware.ge.inmemory;

import com.google.common.collect.Maps;
import com.mware.ge.*;
import com.mware.ge.inmemory.mutations.*;
import com.mware.ge.property.MutablePropertyImpl;
import com.mware.ge.values.storable.StreamingPropertyValue;
import com.mware.ge.values.storable.StreamingPropertyValueRef;
import com.mware.ge.util.IncreasingTime;
import com.mware.ge.util.LookAheadIterable;
import com.mware.ge.values.storable.Value;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class InMemoryTableElement<TElement extends InMemoryElement> implements Serializable {
    private final String id;
    private ReadWriteLock mutationLock= new ReentrantReadWriteLock();
    private TreeSet<Mutation> mutations = new TreeSet<>();

    protected InMemoryTableElement(String id) {
        if (null == id) {
            throw new GeException("Element id is null");
        }
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void addAll(Mutation... newMutations) {
        mutationLock.writeLock().lock();
        try {
            Collections.addAll(mutations, newMutations);
        } finally {
            mutationLock.writeLock().unlock();
        }
    }

    public long getFirstTimestamp() {
        return findFirstMutation(ElementTimestampMutation.class).getTimestamp();
    }

    public <T extends Mutation> T findLastMutation(Class<T> clazz) {
        List<Mutation> filteredMutations = getFilteredMutations(m -> clazz.isAssignableFrom(m.getClass()));
        //noinspection unchecked
        return filteredMutations.isEmpty() ? null : (T) filteredMutations.get(filteredMutations.size() - 1);
    }

    protected <T extends Mutation> T findFirstMutation(Class<T> clazz) {
        List<Mutation> filteredMutations = getFilteredMutations(m -> clazz.isAssignableFrom(m.getClass()));
        //noinspection unchecked
        return filteredMutations.isEmpty() ? null : (T) filteredMutations.get(0);
    }

    protected <T extends Mutation> Iterable<T> findMutations(Class<T> clazz) {
        //noinspection unchecked
        return (Iterable<T>) getFilteredMutations(m -> clazz.isAssignableFrom(m.getClass()));
    }

    public Visibility getVisibility() {
        return findLastMutation(AlterVisibilityMutation.class).getNewVisibility();
    }

    public long getTimestamp() {
        return findLastMutation(ElementTimestampMutation.class).getTimestamp();
    }

    private List<PropertyMutation> findPropertyMutations(Property p) {
        return findPropertyMutations(p.getKey(), p.getName(), p.getVisibility());
    }

    public Property deleteProperty(String key, String name, Authorizations authorizations) {
        return deleteProperty(key, name, null, authorizations);
    }

    public Property getProperty(String key, String name, Visibility visibility, FetchHints fetchHints, Authorizations authorizations) {
        List<PropertyMutation> propertyMutations = findPropertyMutations(key, name, visibility);
        if (propertyMutations == null || propertyMutations.size() == 0) {
            return null;
        }
        return toProperty(propertyMutations, fetchHints, authorizations);
    }

    public Property deleteProperty(String key, String name, Visibility visibility, Authorizations authorizations) {
        Property p = getProperty(key, name, visibility, FetchHints.ALL_INCLUDING_HIDDEN, authorizations);
        if (p != null) {
            deleteProperty(p);
        }
        return p;
    }

    protected void deleteProperty(Property p) {
        List<PropertyMutation> propertyMutations = findPropertyMutations(p);
        mutationLock.writeLock().lock();
        try {
            this.mutations.removeAll(propertyMutations);
        } finally {
            mutationLock.writeLock().unlock();
        }
    }

    private List<PropertyMutation> findPropertyMutations(String key, String name, Visibility visibility) {
        return getFilteredMutations(m ->
                m instanceof PropertyMutation &&
                        (key == null || ((PropertyMutation)m).getPropertyKey().equals(key))
                        && (name == null || ((PropertyMutation)m).getPropertyName().equals(name))
                        && (visibility == null || ((PropertyMutation)m).getPropertyVisibility().equals(visibility))
        ).stream().map(m -> (PropertyMutation)m).collect(Collectors.toList());
    }

    public Iterable<HistoricalPropertyValue> getHistoricalPropertyValues(
            String key,
            String name,
            Visibility visibility,
            Long startTime,
            Long endTime,
            Authorizations authorizations
    ) {
        List<PropertyMutation> propertyMutations = findPropertyMutations(key, name, visibility);
        List<HistoricalPropertyValue> historicalPropertyValues = new ArrayList<>();

        /*
         * There is the expectation that historical property values are a snapshot of the property in
         * time. This method attempts to reconstruct the property current state from mutations.
         */
        Map<String, HistoricalPropertyValue.HistoricalPropertyValueBuilder> currentPropertyBuilders = Maps.newHashMap();
        Set<Visibility> hiddenVisibilities = new HashSet<>();

        for (PropertyMutation m : propertyMutations) {
            String propertyIdentifier = m.getPropertyKey() + m.getPropertyName();
            HistoricalPropertyValue.HistoricalPropertyValueBuilder builder = currentPropertyBuilders.computeIfAbsent(
                    propertyIdentifier,
                    k -> new HistoricalPropertyValue.HistoricalPropertyValueBuilder(m.getPropertyKey(), m.getPropertyName(), m.getTimestamp())
            );

            if (startTime != null && m.getTimestamp() < startTime) {
                continue;
            }
            if (endTime != null && m.getTimestamp() > endTime) {
                continue;
            }
            if (!canRead(m.getVisibility(), authorizations)) {
                continue;
            }
            // Ignore workspace interactions to avoid duplicated entries
            if (m.getVisibility() != null && m.getPropertyVisibility().getVisibilityString().matches("(.*)WORKSPACE(.*)")) {
                continue;
            }

            if (m instanceof SoftDeletePropertyMutation) {
                builder.isDeleted(true);
                builder.timestamp(m.getTimestamp());
                historicalPropertyValues.add(builder.build());
            } else if (m instanceof AddPropertyMetadataMutation) {
                builder.metadata(((AddPropertyMetadataMutation) m).getMetadata(FetchHints.ALL));
                builder.timestamp(m.getTimestamp());
            } else if (m instanceof MarkPropertyHiddenMutation) {
                // Ignore
            } else if (m instanceof MarkPropertyVisibleMutation) {
                // Ignore
            } else if (m instanceof AddPropertyValueMutation) {
                AddPropertyValueMutation apvm = (AddPropertyValueMutation) m;
                Value value = apvm.getValue();
                value = loadIfStreamingPropertyValue(value, m.getTimestamp());

                builder.propertyVisibility(m.getPropertyVisibility())
                        .timestamp(m.getTimestamp())
                        .value(value)
                        .metadata(apvm.getMetadata(FetchHints.ALL))
                        .hiddenVisibilities(hiddenVisibilities)
                        .isDeleted(false);

                // Property modifications use a soft delete immediately followed by an AddPropertyValueMutation.
                // If the condition occurs, remove the delete event from the set.
                if(historicalPropertyValues.size() > 0) {
                    HistoricalPropertyValue last = historicalPropertyValues.get(historicalPropertyValues.size() - 1);
                    if (propertyIdentifier.equals(last.getPropertyKey() + last.getPropertyName()) && last.isDeleted()) {
                        historicalPropertyValues.remove(historicalPropertyValues.size() - 1);
                    }
                }

                historicalPropertyValues.add(builder.build());
            } else {
                throw new GeException("Unhandled PropertyMutation: " + m.getClass().getName());
            }
        }

        Collections.reverse(historicalPropertyValues);
        return historicalPropertyValues;
    }

    public Iterable<Property> getProperties(final FetchHints fetchHints, Long endTime, final Authorizations authorizations) {
        final TreeMap<String, List<PropertyMutation>> propertiesMutations = new TreeMap<>();
        for (PropertyMutation m : findMutations(PropertyMutation.class)) {
            if (endTime != null && m.getTimestamp() > endTime) {
                continue;
            }

            String mapKey = toMapKey(m);
            List<PropertyMutation> propertyMutations = propertiesMutations.computeIfAbsent(mapKey, k -> new ArrayList<>());
            propertyMutations.add(m);
        }
        return new LookAheadIterable<List<PropertyMutation>, Property>() {
            @Override
            protected boolean isIncluded(List<PropertyMutation> src, Property property) {
                return property != null;
            }

            @Override
            protected Property convert(List<PropertyMutation> propertyMutations) {
                return toProperty(propertyMutations, fetchHints, authorizations);
            }

            @Override
            protected Iterator<List<PropertyMutation>> createIterator() {
                return propertiesMutations.values().iterator();
            }
        };
    }

    private Property toProperty(List<PropertyMutation> propertyMutations, FetchHints fetchHints, Authorizations authorizations) {
        String propertyKey = null;
        String propertyName = null;
        Value value = null;
        Metadata metadata = null;
        long timestamp = 0;
        Set<Visibility> hiddenVisibilities = new HashSet<>();
        Visibility visibility = null;
        boolean softDeleted = false;
        boolean hidden = false;
        for (PropertyMutation m : propertyMutations) {
            if (!canRead(m.getVisibility(), authorizations)) {
                continue;
            }

            propertyKey = m.getPropertyKey();
            propertyName = m.getPropertyName();
            visibility = m.getPropertyVisibility();
            if (m.getTimestamp() > timestamp) {
                timestamp = m.getTimestamp();
            }
            if (m instanceof AddPropertyValueMutation) {
                AddPropertyValueMutation apvm = (AddPropertyValueMutation) m;
                value = apvm.getValue();
                metadata = apvm.getMetadata(fetchHints);
                softDeleted = false;
            } else if (m instanceof AddPropertyMetadataMutation) {
                AddPropertyMetadataMutation apmm = (AddPropertyMetadataMutation) m;
                metadata = apmm.getMetadata(fetchHints);
            } else if (m instanceof SoftDeletePropertyMutation) {
                softDeleted = true;
            } else if (m instanceof MarkPropertyHiddenMutation) {
                hidden = true;
                hiddenVisibilities.add(m.getVisibility());
            } else if (m instanceof MarkPropertyVisibleMutation) {
                hidden = false;
                hiddenVisibilities.remove(m.getVisibility());
            } else {
                throw new GeException("Unhandled PropertyMutation: " + m.getClass().getName());
            }
        }
        if (softDeleted) {
            return null;
        }
        if (!fetchHints.isIncludeHidden() && hidden) {
            return null;
        }
        if (propertyKey == null) {
            return null;
        }
        value = loadIfStreamingPropertyValue(value, timestamp);
        return new MutablePropertyImpl(propertyKey, propertyName, value, metadata, timestamp, hiddenVisibilities, visibility, fetchHints);
    }

    private Value loadIfStreamingPropertyValue(Value value, long timestamp) {
        if (value instanceof StreamingPropertyValueRef) {
            value = loadStreamingPropertyValue((StreamingPropertyValueRef) value, timestamp);
        }
        return value;
    }

    protected StreamingPropertyValue loadStreamingPropertyValue(StreamingPropertyValueRef<?> streamingPropertyValueRef, long timestamp) {
        // There's no need to have a Graph object for the pure in-memory implementation. Subclasses should override.
        return streamingPropertyValueRef.toStreamingPropertyValue(null, timestamp);
    }

    private String toMapKey(PropertyMutation m) {
        return m.getPropertyName() + m.getPropertyKey() + m.getPropertyVisibility().getVisibilityString();
    }

    public void appendSoftDeleteMutation(Long timestamp) {
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }
        addMutation(new SoftDeleteMutation(timestamp));
    }

    public void appendMarkHiddenMutation(Visibility visibility) {
        long timestamp = IncreasingTime.currentTimeMillis();
        addMutation(new MarkHiddenMutation(timestamp, visibility));
    }

    public void appendMarkVisibleMutation(Visibility visibility) {
        long timestamp = IncreasingTime.currentTimeMillis();
        addAll(new MarkVisibleMutation(timestamp, visibility));
    }

    public Property appendMarkPropertyHiddenMutation(
            String key,
            String name,
            Visibility propertyVisibility,
            Long timestamp,
            Visibility visibility,
            Authorizations authorizations
    ) {
        Property prop = getProperty(key, name, propertyVisibility, FetchHints.ALL_INCLUDING_HIDDEN, authorizations);
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }
        addMutation(new MarkPropertyHiddenMutation(key, name, propertyVisibility, timestamp, visibility));
        return prop;
    }

    public Property appendMarkPropertyVisibleMutation(
            String key,
            String name,
            Visibility propertyVisibility,
            Long timestamp,
            Visibility visibility,
            Authorizations authorizations
    ) {
        Property prop = getProperty(key, name, propertyVisibility, FetchHints.ALL_INCLUDING_HIDDEN, authorizations);
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }
        addMutation(new MarkPropertyVisibleMutation(key, name, propertyVisibility, timestamp, visibility));
        return prop;
    }

    public void appendSoftDeletePropertyMutation(String key, String name, Visibility propertyVisibility, Long timestamp) {
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }
        addMutation(new SoftDeletePropertyMutation(timestamp, key, name, propertyVisibility));
    }

    public void appendAlterVisibilityMutation(Visibility newVisibility) {
        long timestamp = IncreasingTime.currentTimeMillis();
        addMutation(new AlterVisibilityMutation(timestamp, newVisibility));
    }

    public void appendAddPropertyValueMutation(String key, String name, Value value, Metadata metadata, Visibility visibility, Long timestamp) {
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }
        addMutation(new AddPropertyValueMutation(timestamp, key, name, value, metadata, visibility));
    }

    public void appendAddPropertyMetadataMutation(String key, String name, Metadata metadata, Visibility visibility, Long timestamp) {
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }
        addMutation(new AddPropertyMetadataMutation(timestamp, key, name, metadata, visibility));
    }

    public void appendAlterEdgeLabelMutation(long timestamp, String newEdgeLabel) {
        addMutation(new AlterEdgeLabelMutation(timestamp, newEdgeLabel));
    }

    public void appendAlterConceptTypeMutation(long timestamp, String newConceptType) {
        addMutation(new AlterConceptTypeMutation(timestamp, newConceptType));
    }

    protected List<Mutation> getFilteredMutations(boolean includeHidden, Long endTime, Authorizations authorizations) {
        return getFilteredMutations(m ->
                canRead(m.getVisibility(), authorizations) &&
                        (endTime == null || m.getTimestamp() <= endTime) &&
                        (includeHidden || !(m instanceof MarkHiddenMutation || m instanceof MarkPropertyHiddenMutation))
        );
    }

    public boolean canRead(FetchHints fetchHints, Authorizations authorizations) {
        // this is just a shortcut so that we don't need to construct evaluators and visibility objects to check for an empty string.
        //noinspection SimplifiableIfStatement
        if (getVisibility().getVisibilityString().length() == 0) {
            return true;
        }

        return authorizations.canRead(getVisibility());
    }

    private static boolean canRead(Visibility visibility, Authorizations authorizations) {
        // this is just a shortcut so that we don't need to construct evaluators and visibility objects to check for an empty string.
        //noinspection SimplifiableIfStatement
        if (visibility.getVisibilityString().length() == 0) {
            return true;
        }
        return authorizations.canRead(visibility);
    }

    public Set<Visibility> getHiddenVisibilities() {
        Set<Visibility> results = new HashSet<>();

        mutationLock.readLock().lock();
        try {
            for (Mutation m : this.mutations) {
                if (m instanceof MarkHiddenMutation) {
                    results.add(m.getVisibility());
                } else if (m instanceof MarkVisibleMutation) {
                    results.remove(m.getVisibility());
                }
            }
        } finally {
            mutationLock.readLock().unlock();
        }
        return results;
    }

    public boolean isHidden(Authorizations authorizations) {
        for (Visibility visibility : getHiddenVisibilities()) {
            if (authorizations.canRead(visibility)) {
                return true;
            }
        }
        return false;
    }

    public TElement createElement(InMemoryGraph graph, FetchHints fetchHints, Authorizations authorizations) {
        return createElement(graph, fetchHints, null, authorizations);
    }

    public final TElement createElement(InMemoryGraph graph, FetchHints fetchHints, Long endTime, Authorizations authorizations) {
        if (endTime != null && getFirstTimestamp() > endTime) {
            return null;
        }
        if (isDeleted(endTime, authorizations)) {
            return null;
        }
        return createElementInternal(graph, fetchHints, endTime, authorizations);
    }

    public boolean isDeleted(Long endTime, Authorizations authorizations) {
        List<Mutation> filteredMutations = getFilteredMutations(m ->
                canRead(m.getVisibility(), authorizations) &&
                        (endTime == null || m.getTimestamp() <= endTime) &&
                        (m instanceof SoftDeleteMutation || m instanceof ElementTimestampMutation)
        );
        return filteredMutations.isEmpty() || filteredMutations.get(filteredMutations.size() - 1) instanceof SoftDeleteMutation;
    }

    protected abstract TElement createElementInternal(InMemoryGraph graph, FetchHints fetchHints, Long endTime, Authorizations authorizations);

    private List<Mutation> getFilteredMutations(Predicate<Mutation> filter) {
        mutationLock.readLock().lock();
        try {
            return this.mutations.stream()
                    .filter(filter)
                    .collect(Collectors.toList());
        } finally {
            mutationLock.readLock().unlock();
        }
    }

    private void addMutation(Mutation mutation) {
        mutationLock.writeLock().lock();
        try {
            this.mutations.add(mutation);
        } finally {
            mutationLock.writeLock().unlock();
        }
    }
}
