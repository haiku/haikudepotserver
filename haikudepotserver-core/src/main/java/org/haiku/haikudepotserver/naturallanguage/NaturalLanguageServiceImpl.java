/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.naturallanguage;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.cayenne.query.Query;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>This service is designed to help with more complex queries around natural languages.</p>
 */

@Service
public class NaturalLanguageServiceImpl implements NaturalLanguageService {

    protected static final Logger LOGGER = LoggerFactory.getLogger(NaturalLanguageServiceImpl.class);

    private static final String PREFIX_BASE_NAME_CLASSPATH = "classpath:";

    private ServerRuntime serverRuntime;
    private List<String> messageSourceBaseNames;

    /**
     * <p>This data cannot change over the life-span of the application server so
     * it is cached in memory.</p>
     */

    private Set<String> naturalLanguageCodesWithLocalizationMessages = null;

    /**
     * <p>Maintains a cache of all of the localized messages.</p>
     */

    private final LoadingCache<String, Properties> allLocalizationMessages;

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
                .build(new CacheLoader<String, Properties>() {
                    @Override
                    public Properties load(String key) {
                        return assembleAllLocalizationMessages(key);
                    }
                });
    }

    private Properties assembleAllLocalizationMessages(String naturalLanguageCode) {
        Properties result = new Properties();

        // language-less case (eg xxx.properties)
        messageSourceBaseNames
                .stream()
                .map((bn) -> getLocalizationMessagesPrimative(null, bn))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(result::putAll);

        // now with the language as suffix (eg xxx_en.properties)
        messageSourceBaseNames
                .stream()
                .map((bn) -> getLocalizationMessagesPrimative(naturalLanguageCode, bn))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(result::putAll);

        return result;
    }

    private boolean hasLocalizationMessagesPrimative(String naturalLanguageCode) {
        return messageSourceBaseNames
                .stream()
                .anyMatch((bn) -> getLocalizationMessagesPrimative(naturalLanguageCode, bn).isPresent());
    }

    private Optional<Properties> getLocalizationMessagesPrimative(String naturalLanguageCode, String naturalLanguageBaseName) {
        Preconditions.checkArgument(
                naturalLanguageBaseName.startsWith(PREFIX_BASE_NAME_CLASSPATH),
                "the base name [" + naturalLanguageBaseName + "] is not valid because it is missing the expected prefix.");

        String resourceNamePrefix = naturalLanguageBaseName.substring(PREFIX_BASE_NAME_CLASSPATH.length());
        String resourceName = "/" + resourceNamePrefix
                + (null == naturalLanguageCode ? "" : "_" + naturalLanguageCode)
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
     * <p>Returns those natural languages that have localization.</p>
     */

    private Set<String> getNaturalLanguageCodesWithLocalizationMessages() {

        if(null == naturalLanguageCodesWithLocalizationMessages) {
            ObjectContext context = serverRuntime.newContext();

            naturalLanguageCodesWithLocalizationMessages =
                    NaturalLanguage.getAll(context)
                            .stream()
                            .map(NaturalLanguage::getCode)
                            .filter(this::hasLocalizationMessagesPrimative)
                            .collect(Collectors.toSet());

            LOGGER.info("did find (and cache) {} natural languages with localization", naturalLanguageCodesWithLocalizationMessages.size());
        }

        return naturalLanguageCodesWithLocalizationMessages;
    }

    private Map<String, Boolean> assembleNaturalLanguageCodeUseMap(Query codeQuery) {
        ObjectContext context = serverRuntime.newContext();
        Map<String, Boolean> result = Maps.newConcurrentMap();
        List<String> usedCodes = context.performQuery(codeQuery);

        for (String naturalLanguageCode : NaturalLanguage.getAllCodes(context)) {
            result.put(
                    naturalLanguageCode,
                    usedCodes.contains(naturalLanguageCode)
            );
        }

        return result;
    }

    /**
     * <p>This method is supplied an EJBQL query that provides a list of the 'true' codes.  It returns
     * a list of all codes that have the 'true' codes set to true.</p>
     */

    private Map<String, Boolean> assembleNaturalLanguageCodeUseMap(String ejbqlCodeQuery) {
        EJBQLQuery query = new EJBQLQuery(ejbqlCodeQuery);
        query.setCacheGroups(HaikuDepot.CacheGroup.NATURAL_LANGUAGE.name());
        query.setCacheStrategy(QueryCacheStrategy.SHARED_CACHE);
        return assembleNaturalLanguageCodeUseMap(query);
    }

    private Map<String,Boolean> getNaturalLanguageCodeHasPkgLocalization() {
        return assembleNaturalLanguageCodeUseMap("SELECT DISTINCT pl.naturalLanguage.code FROM " + PkgLocalization.class.getSimpleName() + " pl");
    }

    private Map<String,Boolean> getNaturalLanguageCodeHasPkgVersionLocalization() {
        return assembleNaturalLanguageCodeUseMap("SELECT DISTINCT pvl.naturalLanguage.code FROM " + PkgVersionLocalization.class.getSimpleName() + " pvl");
    }

    private Map<String,Boolean> getNaturalLanguageCodeHasUserRating() {
        return assembleNaturalLanguageCodeUseMap("SELECT DISTINCT ur.naturalLanguage.code FROM " + UserRating.class.getSimpleName() + " ur");
    }

    /**
     * <p>Returns true if the natural language provided has stored messages.</p>
     */

    @Override
    public boolean hasLocalizationMessages(String naturalLanguageCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(naturalLanguageCode));
        return getNaturalLanguageCodesWithLocalizationMessages().contains(naturalLanguageCode);
    }

    /**
     * <p>Returns true if there is user data stored in the database for this language.</p>
     */

    @Override
    public boolean hasData(String naturalLanguageCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(naturalLanguageCode), "the natural language code must be supplied");

        Boolean userRatingB = getNaturalLanguageCodeHasUserRating().get(naturalLanguageCode);

        if (null != userRatingB && userRatingB) {
            return true;
        }

        Boolean pkgVersionLocalizationB = getNaturalLanguageCodeHasPkgVersionLocalization().get(naturalLanguageCode);

        if (null != pkgVersionLocalizationB && pkgVersionLocalizationB) {
            return true;
        }

        Boolean pkgLocalizationB = getNaturalLanguageCodeHasPkgLocalization().get(naturalLanguageCode);

        if (null != pkgLocalizationB && pkgLocalizationB) {
            return true;
        }

        return false;
    }

    @Override
    public Properties getAllLocalizationMessages(String naturalLanguageCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(naturalLanguageCode), "the natural language code must be supplied");
        return allLocalizationMessages.getUnchecked(naturalLanguageCode);
    }

}
