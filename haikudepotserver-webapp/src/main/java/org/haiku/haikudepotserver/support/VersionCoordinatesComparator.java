/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;

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
        ComparisonChain chain = ComparisonChain.start()
                .compare(o1.getMajor(), o2.getMajor(), naturalStringComparator)
                .compare(o1.getMinor(), o2.getMinor(), naturalStringComparator)
                .compare(o1.getMicro(), o2.getMicro(), naturalStringComparator);

        if (!ignorePrereleaseAndRevision) {
            chain = chain
                    .compare(o1.getPreRelease(), o2.getPreRelease(), Ordering.from(naturalStringComparator).nullsLast())
                    .compare(o1.getRevision(), o2.getRevision(), Ordering.natural().nullsLast());
        }

        return chain.result();
    }
}
