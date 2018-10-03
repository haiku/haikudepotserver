/*
 * Copyright 2014-2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.web;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * <p>Helper (static) methods for the multipage part of the system.</p>
 */

public class NaturalLanguageWebHelper {

    protected static Logger LOGGER = LoggerFactory.getLogger(NaturalLanguageWebHelper.class);

    /**
     * <p>This will look at parameters on the supplied request and will return a natural language.  It will
     * resort to English language if no other language is able to be derived.</p>
     */

    public static NaturalLanguage deriveNaturalLanguage(ObjectContext context, HttpServletRequest request) {
        Preconditions.checkNotNull(context);

        if(null!=request) {
            String naturalLanguageCode = request.getParameter(WebConstants.KEY_NATURALLANGUAGECODE);

            if(!Strings.isNullOrEmpty(naturalLanguageCode)) {
                Optional<NaturalLanguage> naturalLanguageOptional = NaturalLanguage.tryGetByCode(context, naturalLanguageCode);

                if(naturalLanguageOptional.isPresent()) {
                    return naturalLanguageOptional.get();
                }
                else {
                   LOGGER.info("the natural language '{}' was specified, but was not able to be found", naturalLanguageCode);
                }
            }

            // see if we can deduce it from the locale.

            Locale locale = request.getLocale();

            if(null != locale) {
                Iterator<String> langI = Splitter.on(Pattern.compile("[-_]")).split(locale.toLanguageTag()).iterator();

                if(langI.hasNext()) {
                    Optional<NaturalLanguage> naturalLanguageOptional = NaturalLanguage.tryGetByCode(context, langI.next());

                    if(naturalLanguageOptional.isPresent() && naturalLanguageOptional.get().getIsPopular()) {
                        return naturalLanguageOptional.get();
                    }
                }

            }
        }

        return NaturalLanguage.tryGetByCode(context, NaturalLanguage.CODE_ENGLISH).get();
    }

}
