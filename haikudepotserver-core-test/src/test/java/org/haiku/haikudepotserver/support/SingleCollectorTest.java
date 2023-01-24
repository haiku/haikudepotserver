/*
 * Copyright 2022-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support;

import org.fest.assertions.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.Stream;

public class SingleCollectorTest {

    @Test
    public void testOptional_empty() {
        Assertions.assertThat(
                Stream.of().collect(SingleCollector.optional()).isPresent()
        ).isFalse();
    }

    @Test
    public void testOptional_present() {
        Assertions.assertThat(
                Stream.of("a").collect(SingleCollector.optional())
        ).isEqualTo(Optional.of("a"));
    }

    @Test
    public void testOptional_moreThanOne() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            Stream.of("a", "b").collect(SingleCollector.optional());
        });
    }

    @Test
    public void testSingle_empty() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            Stream.of().collect(SingleCollector.single());
        });
    }

    @Test
    public void testSingle_present() {
        Assertions.assertThat(
                Stream.of("a").collect(SingleCollector.optional())
        ).isEqualTo(Optional.of("a"));
    }

    @Test
    public void testSingle_moreThanOne() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            Stream.of("a", "b").collect(SingleCollector.single());
        });
    }

}
