/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support;

import com.google.common.base.Strings;

import java.util.Comparator;

/**
 * <p>This class is designed to simulate the comparison behaviours found in the C++ class
 * BPackageVersion::Compare.</p>
 */

public class VersionCoordinatesComparator implements Comparator<VersionCoordinates> {

    private boolean ignorePrereleaseAndRevision = false;

    private NaturalStringComparator naturalStringComparator = new NaturalStringComparator();

    public VersionCoordinatesComparator() {
    }

    public VersionCoordinatesComparator(boolean ignorePrereleaseAndRevision) {
        this.ignorePrereleaseAndRevision = ignorePrereleaseAndRevision;
    }

    @Override
    public int compare(VersionCoordinates o1, VersionCoordinates o2) {

        int result;

        result = naturalStringComparator.compare(o1.getMajor(), o2.getMajor());

        if(0!=result) {
            return result;
        }

        result = naturalStringComparator.compare(o1.getMinor(), o2.getMinor());

        if(0!=result) {
            return result;
        }

        result = naturalStringComparator.compare(o1.getMicro(), o2.getMicro());

        if(0!=result) {
            return result;
        }

        if(!ignorePrereleaseAndRevision) {
            // an empty string means that this is _not_ a prerelease and so the NULL case is
            // sorted as greater than the not NULL case.

            if (Strings.isNullOrEmpty(o1.getPreRelease())) {
                if (!Strings.isNullOrEmpty(o2.getPreRelease())) {
                    return 1;
                }
            } else {
                if (Strings.isNullOrEmpty(o2.getPreRelease())) {
                    return -1;
                } else {
                    result = naturalStringComparator.compare(o1.getPreRelease(), o2.getPreRelease());

                    if (0 != result) {
                        return result;
                    }
                }
            }

            int r1 = null != o1.getRevision() ? o1.getRevision() : 0;
            int r2 = null != o2.getRevision() ? o2.getRevision() : 0;

            return r1-r2;
        }

        return 0;
    }
}
