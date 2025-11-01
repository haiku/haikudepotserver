/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.desktopapplication;

import org.fest.assertions.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

class DesktopApplicationHelperTest {

    @Test
    public void testVersionToString() {
        int[] version = new int[] { 1, 2, 3 };

        // --------------------------
        String result = DesktopApplicationHelper.versionToString(version);
        // --------------------------

        Assertions.assertThat(result).isEqualTo("1.2.3");
    }

    @Test
    public void testTryDeriveVersionFromUserAgentHappyDays() {
        String userAgent = "HaikuDepot/1.2.3";

        // --------------------------
        Optional<int[]> versionOptional = DesktopApplicationHelper.tryDeriveVersionFromUserAgent(userAgent);
        // --------------------------

        Assertions.assertThat(versionOptional.isPresent()).isTrue();
        int[] version = versionOptional.get();
        Assertions.assertThat(version).isEqualTo(new int[] { 1, 2, 3 });
    }

    @Test
    public void testTryDeriveVersionFromUserAgentWhitespace() {
        String userAgent = " HaikuDepot/1.2.3 ";

        // --------------------------
        Optional<int[]> versionOptional = DesktopApplicationHelper.tryDeriveVersionFromUserAgent(userAgent);
        // --------------------------

        Assertions.assertThat(versionOptional.isPresent()).isTrue();
        int[] version = versionOptional.get();
        Assertions.assertThat(version).isEqualTo(new int[] { 1, 2, 3 });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SomethingElse/1.2.3",
            "HaikuDepot/",
            "HaikuDepot/1.2.a",
            "",
    })
    public void testTryDeriveVersionFromUserAgentNoVersion(String userAgent) {

        // --------------------------
        Optional<int[]> versionOptional = DesktopApplicationHelper.tryDeriveVersionFromUserAgent(userAgent);
        // --------------------------

        Assertions.assertThat(versionOptional.isPresent()).isFalse();
    }

}