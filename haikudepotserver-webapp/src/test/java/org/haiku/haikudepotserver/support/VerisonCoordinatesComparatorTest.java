/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import org.fest.assertions.Assertions;
import org.junit.Test;

/**
 * <p>Note that there do not appear to be any automated tests for this in the C++ code.</p>
 */

public class VerisonCoordinatesComparatorTest {

    private VersionCoordinatesComparator versionCoordinatesComparator = new VersionCoordinatesComparator();

    @Test
    public void testCarootcertificatesVersionComparison() {
        VersionCoordinates older = new VersionCoordinates(
                "2014_08_13",
                null,
                null,
                null,
                1);

        VersionCoordinates newer = new VersionCoordinates(
                "2015_02_25",
                null,
                null,
                null,
                1);

        Assertions.assertThat(versionCoordinatesComparator.compare(newer,older)).isGreaterThan(0);
    }

    @Test
    public void testOpensslVersionComparison_a() {

        VersionCoordinates newer = new VersionCoordinates(
                "6",
                "9p1",
                null,
                null,
                1);

        VersionCoordinates older = new VersionCoordinates(
                "6",
                "0p1",
                null,
                null,
                8);

        Assertions.assertThat(versionCoordinatesComparator.compare(newer,older)).isGreaterThan(0);

    }

    @Test
    public void testOpensslVersionComparison_b() {

        VersionCoordinates newer = new VersionCoordinates(
                "6",
                "4p1",
                null,
                null,
                1);

        VersionCoordinates older = new VersionCoordinates(
                "6",
                "2p1",
                null,
                null,
                8);

        Assertions.assertThat(versionCoordinatesComparator.compare(newer,older)).isGreaterThan(0);

    }

    @Test
    public void testVersionComparison() {

        Assertions.assertThat(
                versionCoordinatesComparator.compare(
                        new VersionCoordinates("1","2","3",null,null),
                        new VersionCoordinates("1","2","4",null,null)))
                .isLessThan(0);

        Assertions.assertThat(
                versionCoordinatesComparator.compare(
                        new VersionCoordinates("1","2","3",null,1),
                        new VersionCoordinates("1","2","4",null,null)))
                .isLessThan(0);

        Assertions.assertThat(
                versionCoordinatesComparator.compare(
                        new VersionCoordinates("1","2","3",null,3),
                        new VersionCoordinates("1","2","3",null,2)))
                .isGreaterThan(0);

        Assertions.assertThat(
                versionCoordinatesComparator.compare(
                        new VersionCoordinates("1","2","3","r23",null),
                        new VersionCoordinates("1","2","3",null,null)))
                .isLessThan(0);
    }

}
