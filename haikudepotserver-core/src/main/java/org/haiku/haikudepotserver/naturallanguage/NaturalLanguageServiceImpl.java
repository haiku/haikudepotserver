/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.naturallanguage;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.validation.constraints.NotNull;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ColumnSelect;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.haiku.haikudepotserver.reference.model.NaturalLanguageCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * <p>This service is designed to help with more complex queries around natural languages.</p>
 */

@Service
public class NaturalLanguageServiceImpl implements NaturalLanguageService {

    protected static final Logger LOGGER = LoggerFactory.getLogger(NaturalLanguageServiceImpl.class);

    private static final String PREFIX_BASE_NAME_CLASSPATH = "classpath:";

    private final ServerRuntime serverRuntime;
    private final List<String> messageSourceBaseNames;

    private final Lock naturalLanguageCoordinatesWithLocalizationMessagesLock = new ReentrantLock();

    /**
     * <p>This data cannot change over the life-span of the application server so
     * it is cached in memory.</p>
     */

    private Set<NaturalLanguageCoordinates> naturalLanguageCoordinatesWithLocalizationMessages = null;

    /**
     * <p>Maintains a cache of all of the localized messages.</p>
     */

    private final LoadingCache<NaturalLanguageCoordinates, Properties> allLocalizationMessages;

    public NaturalLanguageServiceImpl(
            ServerRuntime serverRuntime,
            @Qualifier("messageSourceBaseNames") List<String> messageSourceBaseNames
    ) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.messageSourceBaseNames = Preconditions.checkNotNull(messageSourceBaseNames);

        allLocalizationMessages = CacheBuilder
                .newBuilder()
                .maximumSize(5)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .build(new CacheLoader<>() {
                    @Override
                    public Properties load(@NotNull NaturalLanguageCoordinates key) {
                        return assembleAllLocalizationMessagesUncached(key);
                    }
                });
    }

    private Properties assembleAllLocalizationMessagesUncached(NaturalLanguageCoordinates naturalLanguageCoordinates) {
        Properties result = new Properties();

        // language-less case (eg xxx.properties)
        messageSourceBaseNames
                .stream()
                .map((bn) -> tryGetLocalizationMessagesUncached(null, bn))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(result::putAll);

        // now with the language as suffix (eg xxx_en.properties)
        messageSourceBaseNames
                .stream()
                .map((bn) -> tryGetLocalizationMessagesUncached(naturalLanguageCoordinates, bn))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(result::putAll);

        return result;
    }

    private boolean hasLocalizationMessagesUncached(NaturalLanguageCoordinates naturalLanguageCoordinates) {
        return messageSourceBaseNames
                .stream()
                .anyMatch((bn) -> tryGetLocalizationMessagesUncached(naturalLanguageCoordinates, bn).isPresent());
    }

    private Optional<Properties> tryGetLocalizationMessagesUncached(
            NaturalLanguageCoordinates naturalLanguageCoordinates,
            String resourceBase) {
        Preconditions.checkArgument(
                resourceBase.startsWith(PREFIX_BASE_NAME_CLASSPATH),
                "the base name [" + resourceBase + "] is not valid because it is missing the expected prefix.");

        String resourceNamePrefix = resourceBase.substring(PREFIX_BASE_NAME_CLASSPATH.length());
        String resourceName = "/" + resourceNamePrefix
                + (null == naturalLanguageCoordinates ? "" : "_" + naturalLanguageCoordinates.getCode())
                + ".properties";

        try (InputStream inputStream = this.getClass().getResourceAsStream(resourceName)) {
            if(null != inputStream) {
                Properties properties = new Properties();
                properties.load(new InputStreamReader(inputStream, Charsets.UTF_8));
                return properties.isEmpty() ? Optional.empty() : Optional.of(properties);
            }
        } catch (IOException ioe) {
            throw new IllegalStateException("unable to check for presence of natural language localization", ioe);
        }

        return Optional.empty();
    }

    /**
     * <p>This method is supplied an EJBQL query that provides a list of the 'true' codes.  It returns
     * a list of all codes that have the 'true' codes set to true.</p>
     */

    private Set<NaturalLanguageCoordinates> assembleNaturalLanguageCodeUseSet(ColumnSelect<Object[]> select) {
        ObjectContext context = serverRuntime.newContext();
        Set<NaturalLanguageCoordinates> naturalLanguageCoordinates = new HashSet<>();
        select
                .distinct()
                .cacheGroup(HaikuDepot.CacheGroup.NATURAL_LANGUAGE.name())
                .sharedCache()
                .iterate(context, (row) -> naturalLanguageCoordinates.add(new NaturalLanguageCoordinates(
                            Optional.ofNullable(row[0]).map(Object::toString).orElse(null),
                            Optional.ofNullable(row[1]).map(Object::toString).orElse(null),
                            Optional.ofNullable(row[2]).map(Object::toString).orElse(null))));
        return naturalLanguageCoordinates;
    }

    private Set<NaturalLanguageCoordinates> getNaturalLanguageCoordinatesWithPkgLocalization() {
        return assembleNaturalLanguageCodeUseSet(ObjectSelect.columnQuery(
                        PkgLocalization.class,
                        PkgLocalization.NATURAL_LANGUAGE.dot(NaturalLanguage.LANGUAGE_CODE),
                        PkgLocalization.NATURAL_LANGUAGE.dot(NaturalLanguage.COUNTRY_CODE),
                        PkgLocalization.NATURAL_LANGUAGE.dot(NaturalLanguage.SCRIPT_CODE)));
    }

    private Set<NaturalLanguageCoordinates> getNaturalLanguageCoordinatesWithPkgVersionLocalization() {
        return assembleNaturalLanguageCodeUseSet(ObjectSelect.columnQuery(
                        PkgVersionLocalization.class,
                        PkgVersionLocalization.NATURAL_LANGUAGE.dot(NaturalLanguage.LANGUAGE_CODE),
                        PkgVersionLocalization.NATURAL_LANGUAGE.dot(NaturalLanguage.COUNTRY_CODE),
                        PkgVersionLocalization.NATURAL_LANGUAGE.dot(NaturalLanguage.SCRIPT_CODE)));
    }

    private Set<NaturalLanguageCoordinates> getNaturalLanguageCoordinatesWithUserRating() {
        return assembleNaturalLanguageCodeUseSet(ObjectSelect.columnQuery(
                        UserRating.class,
                        UserRating.NATURAL_LANGUAGE.dot(NaturalLanguage.LANGUAGE_CODE),
                        UserRating.NATURAL_LANGUAGE.dot(NaturalLanguage.COUNTRY_CODE),
                        UserRating.NATURAL_LANGUAGE.dot(NaturalLanguage.SCRIPT_CODE)));
    }

    @Override
    public Properties getAllLocalizationMessages(NaturalLanguageCoordinates naturalLanguageCoordinates) {
        Preconditions.checkArgument(null != naturalLanguageCoordinates, "the natural language code must be supplied");
        return allLocalizationMessages.getUnchecked(naturalLanguageCoordinates);
    }

    @Override
    public Set<NaturalLanguageCoordinates> findNaturalLanguagesWithLocalizationMessages() {
        try {
            naturalLanguageCoordinatesWithLocalizationMessagesLock.lock();

            if (null == naturalLanguageCoordinatesWithLocalizationMessages) {
                ObjectContext context = serverRuntime.newContext();

                naturalLanguageCoordinatesWithLocalizationMessages =
                        NaturalLanguage.getAll(context)
                                .stream()
                                .map(NaturalLanguage::toCoordinates)
                                .filter(this::hasLocalizationMessagesUncached)
                                .collect(Collectors.toSet());

                LOGGER.info("did find (and cache) {} natural languages with localization", naturalLanguageCoordinatesWithLocalizationMessages.size());
            }

            return naturalLanguageCoordinatesWithLocalizationMessages;
        }
        finally {
            naturalLanguageCoordinatesWithLocalizationMessagesLock.unlock();
        }
    }

    @Override
    public Set<NaturalLanguageCoordinates> findNaturalLanguagesWithData() {
        Set<NaturalLanguageCoordinates> result = new HashSet<>();
        result.addAll(getNaturalLanguageCoordinatesWithUserRating());
        result.addAll(getNaturalLanguageCoordinatesWithPkgVersionLocalization());
        result.addAll(getNaturalLanguageCoordinatesWithPkgLocalization());
        return Collections.unmodifiableSet(result);
    }
}
