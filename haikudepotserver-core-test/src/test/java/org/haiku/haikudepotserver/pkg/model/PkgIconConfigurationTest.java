/*
 * Copyright 2016-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.dataobjects.MediaType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class PkgIconConfigurationTest {

    public static Stream<Triple<PkgIconConfiguration, PkgIconConfiguration, Integer>> provideComparisonExamples() {
        MediaType mediaTypeA = new MediaType();
        MediaType mediaTypeB = new MediaType();

        mediaTypeA.writeProperty("code", "a");
        mediaTypeB.writeProperty("code", "b");

        return Stream.of(
                ImmutableTriple.of(new PkgIconConfiguration(mediaTypeA, 32), new PkgIconConfiguration(mediaTypeA, 16), -1),
                ImmutableTriple.of(new PkgIconConfiguration(mediaTypeA, 16), new PkgIconConfiguration(mediaTypeA, 32), 1),
                ImmutableTriple.of(new PkgIconConfiguration(mediaTypeA, 16), new PkgIconConfiguration(mediaTypeA, 16), 0),
                ImmutableTriple.of(new PkgIconConfiguration(mediaTypeA, null), new PkgIconConfiguration(mediaTypeA, 32), 1),
                ImmutableTriple.of(new PkgIconConfiguration(mediaTypeA, 32), new PkgIconConfiguration(mediaTypeA, null), -1),
                ImmutableTriple.of(new PkgIconConfiguration(mediaTypeB, 32), new PkgIconConfiguration(mediaTypeA, 32), -1),
                ImmutableTriple.of(new PkgIconConfiguration(mediaTypeA, 32), new PkgIconConfiguration(mediaTypeB, 32), 1)
        );
    }

    @ParameterizedTest
    @MethodSource("provideComparisonExamples")
    public void testComparable(Triple<PkgIconConfiguration, PkgIconConfiguration, Integer> input) {
        PkgIconConfiguration pkgIconConfiguration1 = input.getLeft();
        PkgIconConfiguration pkgIconConfiguration2 = input.getMiddle();
        int expected = input.getRight();

        // ---------------------------------
        int actual = pkgIconConfiguration1.compareTo(pkgIconConfiguration2);
        // ---------------------------------

        Assertions.assertThat(actual).isEqualTo(expected);
    }

}
