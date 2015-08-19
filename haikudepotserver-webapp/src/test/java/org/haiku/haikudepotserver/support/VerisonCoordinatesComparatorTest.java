/*
 * Copyright 2014, Andrew Lindesay
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
