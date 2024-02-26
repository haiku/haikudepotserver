/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.reference.model;

import org.fest.assertions.Assertions;
import org.junit.jupiter.api.Test;

public class NaturalLanguageCoordinatesTest {

    @Test
    public void testFromCode_language() {
        // ------------------------------------
        NaturalLanguageCoordinates coordinates = NaturalLanguageCoordinates.fromCode("fr");
        // ------------------------------------

        Assertions.assertThat(coordinates)
                .isEqualTo(new NaturalLanguageCoordinates("fr", null, null));
    }

    @Test
    public void testFromCode_languageScript() {
        // ------------------------------------
        NaturalLanguageCoordinates coordinates = NaturalLanguageCoordinates.fromCode("fr__Scri");
        // ------------------------------------

        Assertions.assertThat(coordinates)
                .isEqualTo(new NaturalLanguageCoordinates("fr", null, "Scri"));
    }

    @Test
    public void testFromCode_badLanguage() {
        // ------------------------------------
        MalformedNaturalLanguageCodeException exception = org.junit.jupiter.api.Assertions.assertThrows(
                MalformedNaturalLanguageCodeException.class,
                () -> NaturalLanguageCoordinates.fromCode("fR"));
        // ------------------------------------

        Assertions.assertThat(exception.getMessage())
                .isEqualTo("bad language code [fR]");
    }

}
