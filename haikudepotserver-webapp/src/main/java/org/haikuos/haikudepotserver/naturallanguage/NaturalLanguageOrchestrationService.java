/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.naturallanguage;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.cayenne.query.Query;
import org.haikuos.haikudepotserver.dataobjects.NaturalLanguage;
import org.haikuos.haikudepotserver.dataobjects.PkgVersionLocalization;
import org.haikuos.haikudepotserver.dataobjects.UserRating;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * <p>This service is designed to help with more complex queries around natural languages.</p>
 */

@Service
public class NaturalLanguageOrchestrationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(NaturalLanguageOrchestrationService.class);

    private Set<String> naturalLanguageCodesWithLocalizationMessages = null;

    private Map<String,Boolean> naturalLanguageCodeHasUserRating = null;

    private Map<String,Boolean> naturalLanguageCodeHasPkgVersionLocalization = null;

    @Resource
    ServerRuntime serverRuntime;

    private boolean hasLocalizationMessagesPrimative(NaturalLanguage naturalLanguage) {

        // see if we can obtain a properties file for this language
        // and if we can; is there actually anything in it?

        String path;

        if(naturalLanguage.getCode().equals(NaturalLanguage.CODE_ENGLISH)) {
            path = "/messages.properties";
        }
        else {
            path = String.format("/messages_%s.properties", naturalLanguage.getCode());
        }

        try (InputStream inputStream = getClass().getResourceAsStream(path)) {

            if(null == inputStream) {
                return false;
            }

            Properties properties = new Properties();
            properties.load(inputStream);

            if(0==properties.size()) {
                return false;
            }

            return true;
        }
        catch(IOException ioe) {
            throw new IllegalStateException("unable to check if the natural language "+naturalLanguage.getCode()+" has localization present");
        }

    }

    /**
     * <p>Returns those natural languages that have localization.</p>
     */

    private Set<String> getNaturalLanguageCodesWithLocalizationMessages() {

        if(null == naturalLanguageCodesWithLocalizationMessages) {
            ObjectContext context = serverRuntime.getContext();

            naturalLanguageCodesWithLocalizationMessages = ImmutableSet.copyOf(
                    Iterables.transform(
                            Iterables.filter(
                                    NaturalLanguage.getAll(context),
                                    new Predicate<NaturalLanguage>() {
                                        @Override
                                        public boolean apply(NaturalLanguage input) {
                                           return hasLocalizationMessagesPrimative(input);
                                        }
                                    }
                            ),
                            new Function<NaturalLanguage, String>() {
                                @Override
                                public String apply(NaturalLanguage input) {
                                    return input.getCode();
                                }
                            }
                    )
            );

            LOGGER.info("did find (and cache) {} natural languages with localization", naturalLanguageCodesWithLocalizationMessages.size());
        }

        return naturalLanguageCodesWithLocalizationMessages;
    }

    private Map<String,Boolean> assembleNaturalLanguageCodeUseMap(ObjectContext context, Query codeQuery) {
        Map<String,Boolean> result = Maps.newConcurrentMap();
        List<String> usedCodes = context.performQuery(codeQuery);

        for (String naturalLanguageCode : NaturalLanguage.getAllCodes(context)) {
            result.put(
                    naturalLanguageCode,
                    usedCodes.contains(naturalLanguageCode)
            );
        }

        return result;
    }

    private Map<String,Boolean> getNaturalLanguageCodeHasPkgVersionLocalization() {

        if(null==naturalLanguageCodeHasPkgVersionLocalization) {
            naturalLanguageCodeHasPkgVersionLocalization = assembleNaturalLanguageCodeUseMap(
                    serverRuntime.getContext(),
                    new EJBQLQuery("SELECT DISTINCT pvl.naturalLanguage.code FROM " + PkgVersionLocalization.class.getSimpleName() + " pvl")
            );
        }

        return naturalLanguageCodeHasPkgVersionLocalization;
    }

    private Map<String,Boolean> getNaturalLanguageCodeHasUserRating() {

        if(null==naturalLanguageCodeHasUserRating) {
            naturalLanguageCodeHasUserRating = assembleNaturalLanguageCodeUseMap(
                    serverRuntime.getContext(),
                    new EJBQLQuery("SELECT DISTINCT ur.naturalLanguage.code FROM " + UserRating.class.getSimpleName() + " ur")
            );
        }

        return naturalLanguageCodeHasUserRating;
    }

    /**
     * <p>Returns true if the natural language provided has stored messages.</p>
     */

    public boolean hasLocalizationMessages(String naturalLanguageCode) {
        Preconditions.checkState(!Strings.isNullOrEmpty(naturalLanguageCode));
        return getNaturalLanguageCodesWithLocalizationMessages().contains(naturalLanguageCode);
    }

    /**
     * <p>Returns true if there is user data stored in the database for this language.</p>
     */

    public boolean hasData(String naturalLanguageCode) {
        Preconditions.checkState(!Strings.isNullOrEmpty(naturalLanguageCode));

        Boolean userRatingB = getNaturalLanguageCodeHasUserRating().get(naturalLanguageCode);

        if(null!=userRatingB && userRatingB) {
            return true;
        }

        Boolean pkgVersionLocalizationB = getNaturalLanguageCodeHasPkgVersionLocalization().get(naturalLanguageCode);

        if(null!=pkgVersionLocalizationB && pkgVersionLocalizationB) {
            return true;
        }

        return false;
    }

    /**
     * <p>This is invoked from elsewhere in the system in order to update the cached data when an operator has
     * added a new pkg version localization.  This will be used to update caches on this service.</p>
     */

    public void setHasPkgVersionLocalization(String naturalLanguageCode) {
        Preconditions.checkState(!Strings.isNullOrEmpty(naturalLanguageCode));
        getNaturalLanguageCodeHasPkgVersionLocalization().put(naturalLanguageCode, Boolean.TRUE);
    }

    /**
     * <p>This is invoked from elsewhere in the system in order to update the cached data when an operator has
     * added a new user rating.  This will be used to update caches on this service.</p>
     */

    public void setHasUserRating(String naturalLanguageCode) {
        Preconditions.checkState(!Strings.isNullOrEmpty(naturalLanguageCode));
        getNaturalLanguageCodeHasPkgVersionLocalization().put(naturalLanguageCode, Boolean.TRUE);
    }

}
