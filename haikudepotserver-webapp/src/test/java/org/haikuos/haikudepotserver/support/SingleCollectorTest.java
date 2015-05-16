package org.haikuos.haikudepotserver.support;

import org.fest.assertions.Assertions;
import org.junit.Test;

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

    @Test(expected = IllegalStateException.class)
    public void testOptional_moreThanOne() {
        Arrays.asList("a","b").stream().collect(SingleCollector.optional());
    }

    @Test(expected = IllegalStateException.class)
    public void testSingle_empty() {
        Collections.emptyList().stream().collect(SingleCollector.single());
    }

    @Test
    public void testSingle_present() {
        Assertions.assertThat(
                Collections.singletonList("a").stream().collect(SingleCollector.optional())
        ).isEqualTo(Optional.of("a"));
    }

    @Test(expected = IllegalStateException.class)
    public void testSingle_moreThanOne() {
        Arrays.asList("a","b").stream().collect(SingleCollector.single());
    }

}
