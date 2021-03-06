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
package com.mware.ge.accumulo.iterator.model;

import com.mware.ge.accumulo.iterator.util.DataOutputStreamUtils;
import org.apache.accumulo.core.data.ByteSequence;
import org.apache.accumulo.core.data.Value;
import org.apache.hadoop.io.Text;
import org.xerial.snappy.SnappyOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public abstract class ElementData {
    public static final byte[] HEADER = new byte[]{'V', 'E', 'R', 'T', '1'};
    public static final byte[] SNAPPY_HEADER = new byte[]{'S', 'N', 'A', 'P', '1'}; // must be same length as HEADER
    public static final byte TYPE_ID_VERTEX = 1;
    public static final byte TYPE_ID_EDGE = 2;
    public static final int PROP_START = 1;
    public static final int PROP_END = 2;
    public static final int METADATA_START = 3;
    public static final int METADATA_END = 4;
    public Text id;
    public long timestamp;
    public Text visibility;
    public final List<Text> hiddenVisibilities = new ArrayList<>();
    public long softDeleteTimestamp;
    public final List<SoftDeletedProperty> softDeletedProperties = new ArrayList<>();
    public final List<HiddenProperty> hiddenProperties = new ArrayList<>();
    public final List<IteratorMetadataEntry> metadataEntries = new ArrayList<>();
    public final Map<ByteSequence, List<Integer>> propertyMetadata = new HashMap<>();
    public final Map<ByteSequence, PropertyColumnQualifierByteSequence> propertyColumnQualifiers = new HashMap<>();
    public final Map<ByteSequence, byte[]> propertyValues = new HashMap<>();
    public final Map<ByteSequence, ByteSequence> propertyVisibilities = new HashMap<>();
    public final Map<ByteSequence, Long> propertyTimestamps = new HashMap<>();
    public final Set<String> extendedTableNames = new HashSet<>();

    public void clear() {
        id = null;
        visibility = null;
        timestamp = 0;
        softDeleteTimestamp = 0;
        hiddenVisibilities.clear();
        softDeletedProperties.clear();
        hiddenProperties.clear();
        metadataEntries.clear();
        propertyMetadata.clear();
        propertyColumnQualifiers.clear();
        propertyValues.clear();
        propertyVisibilities.clear();
        propertyTimestamps.clear();
        extendedTableNames.clear();
    }

    public final Value encode(IteratorFetchHints fetchHints, boolean compressTransfer) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        OutputStream out;
        if (compressTransfer) {
            bout.write(SNAPPY_HEADER);
            bout.flush();
            out = new SnappyOutputStream(bout);
        } else {
            out = bout;
        }
        try (DataOutputStream dout = new DataOutputStream(out)) {
            encode(dout, fetchHints);
        }
        out.close();
        return new Value(bout.toByteArray());
    }

    public final Value encode(IteratorFetchHints fetchHints) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(out);
        encode(dout, fetchHints);
        return new Value(out.toByteArray());
    }

    protected void encode(DataOutputStream out, IteratorFetchHints fetchHints) throws IOException {
        encodeHeader(out);
        DataOutputStreamUtils.encodeText(out, id);
        out.writeLong(timestamp);
        DataOutputStreamUtils.encodeText(out, visibility);
        DataOutputStreamUtils.encodeTextList(out, hiddenVisibilities);
        encodePropertyMetadataLookup(out);
        encodeProperties(out, fetchHints);
        DataOutputStreamUtils.encodeStringSet(out, extendedTableNames);
    }

    private void encodeHeader(DataOutputStream out) throws IOException {
        out.write(HEADER);
        out.write(getTypeId());
    }

    protected abstract byte getTypeId();

    private void encodePropertyMetadataLookup(DataOutputStream out) throws IOException {
        out.write(METADATA_START);
        DataOutputStreamUtils.encodePropertyMetadataEntry(out, metadataEntries);
        out.write(METADATA_END);
    }

    private void encodeProperties(final DataOutputStream out, IteratorFetchHints fetchHints) throws IOException {
        iterateProperties((
                propertyKey,
                propertyName,
                propertyValue,
                propertyVisibility,
                propertyTimestamp,
                propertyHiddenVisibilities,
                metadata
        ) -> {
            out.write(PROP_START);
            DataOutputStreamUtils.encodeByteSequence(out, propertyKey);
            DataOutputStreamUtils.encodeByteSequence(out, propertyName);
            DataOutputStreamUtils.encodeByteSequence(out, propertyVisibility);
            out.writeLong(propertyTimestamp);
            out.writeInt(propertyValue.length);
            out.write(propertyValue);
            DataOutputStreamUtils.encodeByteSequenceList(out, propertyHiddenVisibilities);
            DataOutputStreamUtils.encodeIntArray(out, metadata);
        }, fetchHints);
        out.write(PROP_END);
    }

    private void iterateProperties(PropertyDataHandler propertyDataHandler, IteratorFetchHints fetchHints) throws IOException {
        boolean includeHidden = fetchHints.isIncludeHidden();
        for (Map.Entry<ByteSequence, byte[]> propertyValueEntry : propertyValues.entrySet()) {
            ByteSequence key = propertyValueEntry.getKey();
            PropertyColumnQualifierByteSequence propertyColumnQualifier = propertyColumnQualifiers.get(key);
            ByteSequence propertyKey = propertyColumnQualifier.getPropertyKey();
            ByteSequence propertyName = propertyColumnQualifier.getPropertyName();
            byte[] propertyValue = propertyValueEntry.getValue();
            ByteSequence propertyVisibility = propertyVisibilities.get(key);
            long propertyTimestamp = propertyTimestamps.get(key);
            if (propertyTimestamp < softDeleteTimestamp) {
                continue;
            }
            Set<ByteSequence> propertyHiddenVisibilities = getPropertyHiddenVisibilities(propertyKey, propertyName, propertyVisibility);
            if (!includeHidden && isHidden(propertyKey, propertyName, propertyVisibility)) {
                continue;
            }
            if (isPropertyDeleted(propertyKey, propertyName, propertyTimestamp, propertyVisibility)) {
                continue;
            }
            List<Integer> metadata = propertyMetadata.get(key);
            propertyDataHandler.handle(
                    propertyKey,
                    propertyName,
                    propertyValue,
                    propertyVisibility,
                    propertyTimestamp,
                    propertyHiddenVisibilities,
                    metadata
            );
        }
    }

    public Iterable<Property> getProperties(IteratorFetchHints fetchHints) {
        final List<Property> results = new ArrayList<>();
        try {
            iterateProperties((
                    propertyKey,
                    propertyName,
                    propertyValue,
                    propertyVisibility,
                    propertyTimestamp,
                    propertyHiddenVisibilities,
                    metadata
            ) -> results.add(new Property(
                    propertyKey,
                    propertyName,
                    propertyValue,
                    propertyVisibility,
                    propertyTimestamp,
                    propertyHiddenVisibilities,
                    metadata
            )), fetchHints);
        } catch (IOException ex) {
            throw new GeAccumuloIteratorException("Could not get properties", ex);
        }
        return results;
    }

    private interface PropertyDataHandler {
        void handle(
                ByteSequence propertyKey,
                ByteSequence propertyName,
                byte[] propertyValue,
                ByteSequence propertyVisibility,
                long propertyTimestamp,
                Set<ByteSequence> propertyHiddenVisibilities,
                List<Integer> metadata
        ) throws IOException;
    }

    private Set<ByteSequence> getPropertyHiddenVisibilities(
            ByteSequence propertyKey,
            ByteSequence propertyName,
            ByteSequence propertyVisibility
    ) {
        Set<ByteSequence> hiddenVisibilities = null;
        for (HiddenProperty hiddenProperty : hiddenProperties) {
            if (hiddenProperty.matches(propertyKey, propertyName, propertyVisibility)) {
                if (hiddenVisibilities == null) {
                    hiddenVisibilities = new HashSet<>();
                }
                hiddenVisibilities.add(hiddenProperty.getHiddenVisibility());
            }
        }
        return hiddenVisibilities;
    }

    private boolean isHidden(ByteSequence propertyKey, ByteSequence propertyName, ByteSequence propertyVisibility) {
        for (HiddenProperty hiddenProperty : hiddenProperties) {
            if (hiddenProperty.matches(propertyKey, propertyName, propertyVisibility)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPropertyDeleted(ByteSequence propertyKey, ByteSequence propertyName, long propertyTimestamp, ByteSequence propertyVisibility) {
        for (SoftDeletedProperty softDeletedProperty : softDeletedProperties) {
            if (softDeletedProperty.matches(propertyKey, propertyName, propertyVisibility)) {
                return softDeletedProperty.getTimestamp() >= propertyTimestamp;
            }
        }
        return false;
    }
}
