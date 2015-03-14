/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.web;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.haikuos.haikudepotserver.dataobjects.NaturalLanguage;

import javax.servlet.http.HttpServletRequest;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * <p>Helper (static) methods for the multipage part of the system.</p>
 */

public class NaturalLanguageWebHelper {

    /**
     * <p>This will look at parameters on the supplied request and will return a natural language.  It will
     * resort to English language if no other language is able to be derived.</p>
     */

    // [apl 10.oct.2014]
    // This will presently only select from the list of popular languages.

    public static NaturalLanguage deriveNaturalLanguage(ObjectContext context, HttpServletRequest request) {
        Preconditions.checkNotNull(context);

        if(null!=request) {
            String naturalLanguageCode = request.getParameter(WebConstants.KEY_NATURALLANGUAGECODE);

            if(!Strings.isNullOrEmpty(naturalLanguageCode)) {
                Optional<NaturalLanguage> naturalLanguageOptional = NaturalLanguage.getByCode(context, naturalLanguageCode);

                if(!naturalLanguageOptional.isPresent() && naturalLanguageOptional.get().getIsPopular()) {
                    throw new IllegalStateException("the natural language for code " + naturalLanguageCode + " was not able to be found");
                }

                return naturalLanguageOptional.get();
            }

            // see if we can deduce it from the locale.

            Locale locale = request.getLocale();

            if(null != locale) {
                Iterator<String> langI = Splitter.on(Pattern.compile("[-_]")).split(locale.toLanguageTag()).iterator();

                if(langI.hasNext()) {
                    Optional<NaturalLanguage> naturalLanguageOptional = NaturalLanguage.getByCode(context, langI.next());

                    if(naturalLanguageOptional.isPresent() && naturalLanguageOptional.get().getIsPopular()) {
                        return naturalLanguageOptional.get();
                    }
                }

            }
        }

        return NaturalLanguage.getByCode(context, NaturalLanguage.CODE_ENGLISH).get();
    }

}
