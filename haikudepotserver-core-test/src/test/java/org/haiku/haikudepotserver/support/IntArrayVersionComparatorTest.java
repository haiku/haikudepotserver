/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.desktopapplication;

import org.haiku.haikudepotserver.support.IntArrayVersionComparator;
import org.junit.Test;
import static org.fest.assertions.Assertions.assertThat;

public class IntArrayVersionComparatorTest {

    private IntArrayVersionComparator comparator = new IntArrayVersionComparator();

    @Test
    public void test_lengthChecks() {
        assertThat(comparator.compare(new int[] { 1,2,3 }, new int[] { 1,2,3 })).isEqualTo(0);
        assertThat(comparator.compare(new int[] { 1,2,3 }, new int[] { 1,2 })).isEqualTo(1);
        assertThat(comparator.compare(new int[] { 1,2 }, new int[] { 1,2,3 })).isEqualTo(-1);
    }

    @Test
    public void test_valueChecks() {
        assertThat(comparator.compare(new int[] { 1,2,3 }, new int[] { 1,3,3 })).isEqualTo(-1);
        assertThat(comparator.compare(new int[] { 1,2,3 }, new int[] { 1,1 })).isEqualTo(1);
        assertThat(comparator.compare(new int[] { 1,2 }, new int[] { 1,2,0 })).isEqualTo(-1);
    }

}
