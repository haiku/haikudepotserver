/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.haiku.haikudepotserver.dataobjects.auto._NaturalLanguage;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <P>This describes a spoken or "natural" language that can be used for localization of the application.  These are
 * referenced by <a href="https://en.wikipedia.org/wiki/ISO_639-1">ISO 639-1</a> values such as "en", "de" etc...</P>
 */

public class NaturalLanguage extends _NaturalLanguage {

    public final static String CODE_ENGLISH = "en";
    public final static String CODE_GERMAN = "de";
    public final static String CODE_SPANISH = "es";
    public final static String CODE_FRENCH = "fr";
    public final static String CODE_RUSSIAN = "ru";
    public final static String CODE_SLOVAK = "sk";

    public static List<NaturalLanguage> getAll(ObjectContext context) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        return ObjectSelect
                .query(NaturalLanguage.class)
                .orderBy(NaturalLanguage.NAME.asc())
                .cacheStrategy(QueryCacheStrategy.SHARED_CACHE)
                .select(context);
    }

    public static List<NaturalLanguage> getAllPopular(ObjectContext context) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        return getAll(context).stream().filter(_NaturalLanguage::getIsPopular).collect(Collectors.toList());
    }

    public static Optional<NaturalLanguage> getByCode(ObjectContext context, final String code) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "the code must be provided");
        return getAll(context).stream().filter(nl -> nl.getCode().equals(code)).collect(SingleCollector.optional());
    }

    public static NaturalLanguage getEnglish(ObjectContext context) {
        return getByCode(context, CODE_ENGLISH).get();
    }

    /**
     * <p>This method will return all of the natural language codes in the system.</p>
     */

    public static List<String> getAllCodes(ObjectContext context) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        return getAll(context).stream().map(NaturalLanguage::getCode).collect(Collectors.toList());
    }

    public boolean isEnglish() {
        return CODE_ENGLISH.equals(getCode());
    }

    /**
     * <p>Can be used to lookup the title of this language in the localization strings.</p>
     */

    public String getTitleKey() {
        return String.format("naturalLanguage.%s",getCode().toLowerCase());
    }

    public Locale toLocale() {
        return Locale.forLanguageTag(getCode());
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("code", getCode())
                .build();
    }

}
