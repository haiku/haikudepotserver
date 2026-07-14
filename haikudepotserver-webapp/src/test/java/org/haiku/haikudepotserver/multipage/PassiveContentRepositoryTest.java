/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage;

import org.fest.assertions.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class PassiveContentRepositoryTest {

    private PassiveContentRepository repository;
    private ResourceLoader resourceLoader;

    @BeforeEach
    public void beforeEach() {
        resourceLoader = Mockito.mock(ResourceLoader.class);
        repository = new PassiveContentRepository(resourceLoader, "classpath:somewhere");
    }

    /**
     * <p>Simulates the case where the data is present for the user's language.</p>
     */
    @Test
    public void getFoundForLocale() {
        Resource resource = createResource("dolphin");
        Mockito.when(resourceLoader.getResource(Mockito.eq("classpath:somewhere/whale_de.html")))
                .thenReturn(resource);

        // ------------------------------------
        String result = repository.get("whale.html", Locale.GERMAN);
        // ------------------------------------

        Assertions.assertThat(result).isEqualTo("dolphin");
    }


    /**
     * <p>Simulates the case where the data is not present for the user's language and so the logic
     * will fall back to English as a default.</p>
     */
    @Test
    public void getMissedForLocale() {
        Resource deResource = createMissingResource();
        Mockito.when(resourceLoader.getResource(Mockito.eq("classpath:somewhere/seagull_de.html")))
                .thenReturn(deResource);

        Resource enResource = createResource("tern");
        Mockito.when(resourceLoader.getResource(Mockito.eq("classpath:somewhere/seagull_en.html")))
                .thenReturn(enResource);

        // ------------------------------------
        String result = repository.get("seagull.html", Locale.GERMAN);
        // ------------------------------------

        Assertions.assertThat(result).isEqualTo("tern");
    }

    private Resource createMissingResource() {
        Resource resource = Mockito.mock(Resource.class);
        Mockito.when(resource.exists()).thenReturn(false);
        return resource;
    }

    private Resource createResource(String content) {
        Resource resource = Mockito.mock(Resource.class);
        Mockito.when(resource.exists()).thenReturn(true);
        try {
            Mockito.when(resource.getContentAsString(Mockito.eq(StandardCharsets.UTF_8)))
                    .thenReturn(content);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        return resource;
    }

}