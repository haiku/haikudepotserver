/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>The resources are able to contain localized chunks of content that are passive;
 * don't contain any dynamic logic. This class is able to look those up by name +
 * locale.</p>
 */
public class PassiveContentRepository {

    private final Pattern PATTERN_LEAF = Pattern.compile("^([a-z0-9-]+)(\\.[a-z0-9]+)$");

    private final ResourceLoader resourceLoader;
    private final String baseUrl;

    private final LoadingCache<LeafAndLocale, String> cache = CacheBuilder
            .newBuilder()
            .maximumSize(25)
            .build(new CacheLoader<>() {
        @Override
        public String load(@SuppressWarnings("NullableProblems") LeafAndLocale key) {
            return getUncached(key);
        }
    });

    public PassiveContentRepository(
            ResourceLoader resourceLoader,
            String baseUrl
    ) {
        Preconditions.checkArgument(StringUtils.isNotBlank(baseUrl));
        Preconditions.checkArgument(!baseUrl.endsWith("/"), "bad base url");
        this.resourceLoader = Preconditions.checkNotNull(resourceLoader);
        this.baseUrl = baseUrl;
    }

    /**
     * <p>Returns the content for the given leaf and locale. It will throw a runtime exception if it's not able to
     * find the content.</p>
     *
     * <p>The resource will be found first with the locale such as {@code somefile_de.html} but if that doesn't exist
     * then it will try the English as a fallback {@code somefile_en.html}.</p>
     */

    public String get(String leaf, Locale locale) {
        return cache.getUnchecked(new LeafAndLocale(leaf, locale));
    }

    private String getUncached(LeafAndLocale key) {
        Resource resource = tryGetResourceUncached(key)
                .orElseThrow(() -> new IllegalStateException(
                        "unable to find the passive content for [%s] / [%s]".formatted(key.leaf, key.locale)));

        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            throw new UncheckedIOException("unable to read the resource [%s]".formatted(resource.getDescription()), ioe);
        }
    }

    private Optional<Resource> tryGetResourceUncached(LeafAndLocale key) {
        Matcher matcher = PATTERN_LEAF.matcher(key.leaf);

        if (!matcher.matches()) {
            throw new IllegalStateException("illegal passive content leaf [%s]".formatted(key.leaf));
        }

        String leafName = matcher.group(1);
        String leafExtension = matcher.group(2);

        String languageCode = key.locale.getLanguage();
        Resource resource = resourceLoader.getResource("%s/%s_%s%s".formatted(baseUrl, leafName, languageCode, leafExtension));

        if (resource.exists()) {
            return Optional.of(resource);
        }

        resource  = resourceLoader.getResource("%s/%s_en%s".formatted(baseUrl, leafName, leafExtension));

        if (resource.exists()) {
            return Optional.of(resource);
        }

        return Optional.empty();
    }

    /**
     * <p>This is used as a cache key for the passive content data.</p>
     */

    private record LeafAndLocale(String leaf, Locale locale) {

        private LeafAndLocale {
            Preconditions.checkArgument(StringUtils.isNotBlank(leaf));
            Preconditions.checkArgument(null != locale);
        }

    }

}
