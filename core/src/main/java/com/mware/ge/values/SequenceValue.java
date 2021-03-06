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
package com.mware.ge.values;

import java.util.Comparator;
import java.util.Iterator;

/**
 * Values that represent sequences of values (such as Lists or Arrays) need to implement this interface.
 * Thus we can get an equality check that is based on the values (e.g. List.equals(ArrayValue) )
 * Values that implement this interface also need to overwrite isSequence() to return true!
 * <p>
 * Note that even though SequenceValue extends Iterable iterating over the sequence using iterator() might not be the
 * most performant method. Branch using iterationPreference() in performance critical code paths.
 */
public interface SequenceValue extends Iterable<AnyValue> {
    /**
     * The preferred way to iterate this sequence. Preferred in this case means the method which is expected to be
     * the most performant.
     */
    enum IterationPreference {
        RANDOM_ACCESS,
        ITERATION
    }

    int length();

    AnyValue value(int offset);

    @Override
    Iterator<AnyValue> iterator();

    IterationPreference iterationPreference();

    default boolean equals(SequenceValue other) {
        if (other == null) {
            return false;
        }

        IterationPreference pref = iterationPreference();
        IterationPreference otherPref = other.iterationPreference();
        if (pref == IterationPreference.RANDOM_ACCESS && otherPref == IterationPreference.RANDOM_ACCESS) {
            return equalsUsingRandomAccess(this, other);
        } else {
            return equalsUsingIterators(this, other);
        }
    }

    static boolean equalsUsingRandomAccess(SequenceValue a, SequenceValue b) {
        int i = 0;
        boolean areEqual = a.length() == b.length();

        while (areEqual && i < a.length()) {
            areEqual = a.value(i).equals(b.value(i));
            i++;
        }
        return areEqual;
    }

    static Boolean ternaryEqualsUsingRandomAccess(SequenceValue a, SequenceValue b) {
        if (a.length() != b.length()) {
            return Boolean.FALSE;
        }

        int i = 0;
        Boolean equivalenceResult = Boolean.TRUE;

        while (i < a.length()) {
            Boolean areEqual = a.value(i).ternaryEquals(b.value(i));
            if (areEqual == null) {
                equivalenceResult = null;
            } else if (!areEqual) {
                return Boolean.FALSE;
            }
            i++;
        }

        return equivalenceResult;
    }

    static boolean equalsUsingIterators(SequenceValue a, SequenceValue b) {
        boolean areEqual = true;
        Iterator<AnyValue> aIterator = a.iterator();
        Iterator<AnyValue> bIterator = b.iterator();

        while (areEqual && aIterator.hasNext() && bIterator.hasNext()) {
            areEqual = aIterator.next().equals(bIterator.next());
        }

        return areEqual && aIterator.hasNext() == bIterator.hasNext();
    }

    static Boolean ternaryEqualsUsingIterators(SequenceValue a, SequenceValue b) {
        Boolean equivalenceResult = Boolean.TRUE;
        Iterator<AnyValue> aIterator = a.iterator();
        Iterator<AnyValue> bIterator = b.iterator();

        while (aIterator.hasNext() && bIterator.hasNext()) {
            Boolean areEqual = aIterator.next().ternaryEquals(bIterator.next());
            if (areEqual == null) {
                equivalenceResult = null;
            } else if (!areEqual) {
                return Boolean.FALSE;
            }
        }

        return aIterator.hasNext() == bIterator.hasNext() ? equivalenceResult : Boolean.FALSE;
    }

    default int compareToSequence(SequenceValue other, Comparator<AnyValue> comparator) {
        IterationPreference pref = iterationPreference();
        IterationPreference otherPref = other.iterationPreference();
        if (pref == IterationPreference.RANDOM_ACCESS && otherPref == IterationPreference.RANDOM_ACCESS) {
            return compareUsingRandomAccess(this, other, comparator);
        } else {
            return compareUsingIterators(this, other, comparator);
        }
    }

    static int compareUsingRandomAccess(SequenceValue a, SequenceValue b, Comparator<AnyValue> comparator) {
        int i = 0;
        int x = 0;
        int length = Math.min(a.length(), b.length());

        while (x == 0 && i < length) {
            x = comparator.compare(a.value(i), b.value(i));
            i++;
        }

        if (x == 0) {
            x = a.length() - b.length();
        }

        return x;
    }

    static int compareUsingIterators(SequenceValue a, SequenceValue b, Comparator<AnyValue> comparator) {
        int x = 0;
        Iterator<AnyValue> aIterator = a.iterator();
        Iterator<AnyValue> bIterator = b.iterator();

        while (aIterator.hasNext() && bIterator.hasNext()) {
            x = comparator.compare(aIterator.next(), bIterator.next());
        }

        if (x == 0) {
            x = Boolean.compare(aIterator.hasNext(), bIterator.hasNext());
        }

        return x;
    }

    default Boolean ternaryEquality(SequenceValue other) {
        IterationPreference pref = iterationPreference();
        IterationPreference otherPref = other.iterationPreference();
        if (pref == IterationPreference.RANDOM_ACCESS && otherPref == IterationPreference.RANDOM_ACCESS) {
            return ternaryEqualsUsingRandomAccess(this, other);
        } else {
            return ternaryEqualsUsingIterators(this, other);
        }
    }
}
