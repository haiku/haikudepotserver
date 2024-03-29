/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.naturallanguage;

import jakarta.annotation.Resource;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoded;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.stream.Stream;

@ContextConfiguration(classes = TestConfig.class)
class NaturalLanguageServiceImplTest extends AbstractIntegrationTest {

    @Resource
    public NaturalLanguageService naturalLanguageService;

    @ParameterizedTest
    @MethodSource("testBestMatchCases")
    public void testBestMatch(
            NaturalLanguageCoordinates toMatchCoordinates,
            NaturalLanguageCoordinates expectedCoordinates) {

        // note that this is a wider range of language options compared to that used in the `LocaleResolver`.
        List<? extends NaturalLanguageCoded> naturalLanguages = naturalLanguageService.getAllSupportedCoordinates()
                .stream()
                .sorted(NaturalLanguageCoded.NATURAL_LANGUAGE_CODE_COMPARATOR)
                .toList();

        // ---------------------------------
        NaturalLanguageCoded actualNaturalLanguage = naturalLanguageService.tryGetBestMatchFromList(naturalLanguages, toMatchCoordinates).orElse(null);
        // ---------------------------------

        if (null == expectedCoordinates) {
            Assertions.assertThat(actualNaturalLanguage).isNull();
        }
        else {
            Assertions.assertThat(NaturalLanguageCoordinates.fromCoded(actualNaturalLanguage)).isEqualTo(expectedCoordinates);
        }
    }

    public static Stream<Arguments> testBestMatchCases() {
        return Stream.of(
                Arguments.of(
                        new NaturalLanguageCoordinates("es", "Latn", "419"),
                        new NaturalLanguageCoordinates("es", null, "419")
                ),
                Arguments.of(
                        new NaturalLanguageCoordinates("zh", "Hans", null),
                        new NaturalLanguageCoordinates("zh", "Hans", null)
                ),
                Arguments.of(
                        new NaturalLanguageCoordinates("en", null, null),
                        new NaturalLanguageCoordinates("en", null, null)
                ),
                Arguments.of(
                        new NaturalLanguageCoordinates("en", null, "NZ"),
                        new NaturalLanguageCoordinates("en", null, null)
                ),
                Arguments.of(
                        new NaturalLanguageCoordinates("en", null, "AU"),
                        new NaturalLanguageCoordinates("en", null, "AU")
                ),
                Arguments.of(
                        new NaturalLanguageCoordinates("aa", null, null),
                        null // not found
                ),
                Arguments.of(
                        new NaturalLanguageCoordinates("zz", null, null),
                        null // not found
                ),
                Arguments.of(
                        new NaturalLanguageCoordinates("en", null, "GB"),
                        new NaturalLanguageCoordinates("en", null, "GB")
                )
        );
    }

}