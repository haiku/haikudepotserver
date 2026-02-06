/*
 * Copyright 2014-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import jakarta.validation.constraints.NotNull;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.haiku.haikudepotserver.dataobjects.auto._NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.support.Coded;
import org.haiku.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoded;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <P>This describes a spoken or "natural" language that can be used for localization of the application.  These are
 * referenced by <a href="https://en.wikipedia.org/wiki/ISO_639-1">ISO 639-1</a> values such as "en", "de" etc...</P>
 */

public class NaturalLanguage extends _NaturalLanguage
        implements Coded, NaturalLanguageCoded, CreateAndModifyTimestamped, Comparable<NaturalLanguage> {

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

    public static Optional<NaturalLanguage> tryGetByNaturalLanguage(ObjectContext context, final NaturalLanguageCoded naturalLanguage) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != naturalLanguage, "the coordinates must be provided");
        return getAll(context).stream().filter(nl -> 0 == NaturalLanguageCoded.NATURAL_LANGUAGE_CODE_COMPARATOR.compare(nl, naturalLanguage)).collect(SingleCollector.optional());
    }

    public static Optional<NaturalLanguage> tryGetByCode(ObjectContext context, final String code) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "the code must be provided");
        return tryGetByNaturalLanguage(context, NaturalLanguageCoordinates.fromCode(code));
    }

    public static NaturalLanguage getByNaturalLanguage(ObjectContext context, final NaturalLanguageCoded naturalLanguage) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != naturalLanguage, "the coded must be provided");
        return tryGetByNaturalLanguage(context, naturalLanguage)
                .orElseThrow(() -> new IllegalStateException(
                        "unable to find natural language with code [" + NaturalLanguageCoordinates.fromCoded(naturalLanguage) + "]"));
    }

    public static NaturalLanguage getByCode(ObjectContext context, final String code) {
        return tryGetByCode(context, code)
                .orElseThrow(() -> new IllegalStateException(
                        "unable to find natural language with code [" + code + "]"));
    }

    public static NaturalLanguage getEnglish(ObjectContext context) {
        return tryGetByNaturalLanguage(context, NaturalLanguageCoordinates.english())
                .orElseThrow(() -> new IllegalStateException("the english language must be present"));
    }

    public String getCode() {
        return toCoordinates().getCode();
    }

    public NaturalLanguageCoordinates toCoordinates() {
        return new NaturalLanguageCoordinates(getLanguageCode(), getScriptCode(), getCountryCode());
    }

    /**
     * <p>Can be used to lookup the title of this language in the localization strings.</p>
     */

    public String getTitleKey() {
        return String.format("naturalLanguage.%s", getCode());
    }

    @Override
    public int compareTo(@NotNull NaturalLanguage other) {
        return NaturalLanguageCoded.NATURAL_LANGUAGE_CODE_COMPARATOR.compare(this, other);
    }

    public int compareTo(@NotNull NaturalLanguageCoordinates coordinates) {
        return toCoordinates().compareTo(coordinates);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("code", getCode())
                .build();
    }

}
