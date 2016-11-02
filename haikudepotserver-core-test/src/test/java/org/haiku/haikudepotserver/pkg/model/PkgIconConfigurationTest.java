/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import com.google.common.collect.ImmutableList;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.dataobjects.MediaType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

@RunWith(Parameterized.class)
public class PkgIconConfigurationTest {

    private PkgIconConfiguration pkgIconConfiguration1;
    private PkgIconConfiguration pkgIconConfiguration2;
    private int expected;

    public PkgIconConfigurationTest(PkgIconConfiguration pkgIconConfiguration1, PkgIconConfiguration pkgIconConfiguration2, int expected) {
        this.pkgIconConfiguration1 = pkgIconConfiguration1;
        this.pkgIconConfiguration2 = pkgIconConfiguration2;
        this.expected = expected;
    }

    @Parameterized.Parameters
    public static Collection provideComparisonExamples() {
        MediaType mediaTypeA = new MediaType();
        MediaType mediaTypeB = new MediaType();

        mediaTypeA.writeProperty("code", "a");
        mediaTypeB.writeProperty("code", "b");

        return ImmutableList.of(
            new Object[] { new PkgIconConfiguration(mediaTypeA, 32), new PkgIconConfiguration(mediaTypeA, 16), -1 },
            new Object[] { new PkgIconConfiguration(mediaTypeA, 16), new PkgIconConfiguration(mediaTypeA, 32), 1 },
            new Object[] { new PkgIconConfiguration(mediaTypeA, 16), new PkgIconConfiguration(mediaTypeA, 16), 0 },
            new Object[] { new PkgIconConfiguration(mediaTypeA, null), new PkgIconConfiguration(mediaTypeA, 32), 1 },
            new Object[] { new PkgIconConfiguration(mediaTypeA, 32), new PkgIconConfiguration(mediaTypeA, null), -1 },
            new Object[] { new PkgIconConfiguration(mediaTypeB, 32), new PkgIconConfiguration(mediaTypeA, 32), -1 },
            new Object[] { new PkgIconConfiguration(mediaTypeA, 32), new PkgIconConfiguration(mediaTypeB, 32), 1 }
        );
    }

    @Test
    public void testComparable() {
        // ---------------------------------
        int actual = pkgIconConfiguration1.compareTo(pkgIconConfiguration2);
        // ---------------------------------

        Assertions.assertThat(actual).isEqualTo(expected);
    }

}
