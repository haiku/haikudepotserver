/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.reference.model;

import org.apache.commons.lang3.StringUtils;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Locale;
import java.util.stream.Stream;

public class NaturalLanguageCoordinatesTest {

    private static Stream<Arguments> testCodeToComponents() {
        return Stream.of(
                Arguments.of("en-US", new String[]{"en", "", "US"}),
                Arguments.of("de", new String[]{"de", "", ""}),
                Arguments.of("sr-Latn-BA", new String[]{"sr", "Latn", "BA"}),
                Arguments.of("zh-Hans", new String[]{"zh", "Hans", ""})
        );
    }

    @ParameterizedTest
    @MethodSource("testCodeToComponents")
    public void testNaturalLanguageCoordinatesParseToComponents(String code, String[] parts) {

        // ------------------------------------
        NaturalLanguageCoordinates coordinates = NaturalLanguageCoordinates.fromCode(code);
        // ------------------------------------

        // ok to trim to null here because the coordinates represent the missing value as null instead
        // of empty string like the Locale does.
        Assertions.assertThat(coordinates.languageCode()).isEqualTo(StringUtils.trimToNull(parts[0]));
        Assertions.assertThat(coordinates.scriptCode()).isEqualTo(StringUtils.trimToNull(parts[1]));
        Assertions.assertThat(coordinates.countryCode()).isEqualTo(StringUtils.trimToNull(parts[2]));
    }

    @ParameterizedTest
    @MethodSource("testCodeToComponents")
    public void testNaturalLanguageCoordinatesToCode(String code, String[] parts) {

        // ------------------------------------
        NaturalLanguageCoordinates coordinates = new NaturalLanguageCoordinates(
                StringUtils.trimToNull(parts[0]),
                StringUtils.trimToNull(parts[1]),
                StringUtils.trimToNull(parts[2]));
        // ------------------------------------

        Assertions.assertThat(coordinates.getCode()).isEqualTo(code);
    }

    @ParameterizedTest
    @MethodSource("testCodeToComponents")
    public void testLocaleParseToComponents(String code, String[] parts) {

        // ------------------------------------
        Locale locale = new Locale.Builder().setLanguageTag(code).build();
        // ------------------------------------

        Assertions.assertThat(locale.getLanguage()).isEqualTo(parts[0]);
        Assertions.assertThat(locale.getScript()).isEqualTo(parts[1]);
        Assertions.assertThat(locale.getCountry()).isEqualTo(parts[2]);
    }

    @ParameterizedTest
    @MethodSource("testCodeToComponents")
    public void testLocaleToCode(String code, String[] parts) {

        // ------------------------------------
        Locale locale = new Locale.Builder()
                .setLanguage(parts[0])
                .setScript(parts[1])
                .setRegion(parts[2])
                .build();
        // ------------------------------------

        Assertions.assertThat(locale.toLanguageTag()).isEqualTo(code);
    }

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
                .isEqualTo(new NaturalLanguageCoordinates("fr", "Scri", null));
    }

    @Test
    public void testFromCode_toCode() {
        // ------------------------------------
        NaturalLanguageCoordinates coordinates = new NaturalLanguageCoordinates("fr", "Scri", null);
        // ------------------------------------

        Assertions.assertThat(coordinates.getCode()).isEqualTo("fr-Scri");
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
