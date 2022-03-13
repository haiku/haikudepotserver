/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support;

import org.fest.assertions.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

public class SingleCollectorTest {

    @Test
    public void testOptional_empty() {
        Assertions.assertThat(
                Collections.emptyList().stream().collect(SingleCollector.optional()).isPresent()
        ).isFalse();
    }

    @Test
    public void testOptional_present() {
        Assertions.assertThat(
                Collections.singletonList("a").stream().collect(SingleCollector.optional())
        ).isEqualTo(Optional.of("a"));
    }

    @Test
    public void testOptional_moreThanOne() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            Arrays.asList("a", "b").stream().collect(SingleCollector.optional());
        });
    }

    @Test
    public void testSingle_empty() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            Collections.emptyList().stream().collect(SingleCollector.single());
        });
    }

    @Test
    public void testSingle_present() {
        Assertions.assertThat(
                Collections.singletonList("a").stream().collect(SingleCollector.optional())
        ).isEqualTo(Optional.of("a"));
    }

    @Test
    public void testSingle_moreThanOne() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            Arrays.asList("a", "b").stream().collect(SingleCollector.single());
        });
    }

}
