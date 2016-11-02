/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import java.util.Comparator;

/**
 * <p>Some versions can be compared on the basis of a series of integers.  For example 1.0.2 can be
 * expressed as an array [1, 0, 2].  This can be compared with another array of integers considered
 * to be a version.  This doesn't apply to Haiku Package Versions, but might be used to compare
 * versions of the desktop application's User-Agent.</p>
 */

public class IntArrayVersionComparator implements Comparator<int[]> {

    /**
     * @return 0 if the version numbers are equal, less than 0 if a is less than b and greater than 0 if a is greater than b
     */

    @Override
    public int compare(int[] a, int[] b) {
        for (int i=0;i<Math.max(a.length, b.length);i++) {
            if (i >= a.length) {
                return -1;
            }

            if(i >= b.length) {
                return 1;
            }

            int c = Integer.compare(a[i],b[i]);

            if(0 != c) {
                return c;
            }
        }

        return 0;
    }

}
