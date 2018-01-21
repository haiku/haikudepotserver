/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.haiku.haikudepotserver.dataobjects.auto._Architecture;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Architecture extends _Architecture {

    public final static String CODE_SOURCE = "source";
    public final static String CODE_ANY = "any";
    public final static String CODE_X86_64 = "x86_64";

    public static List<Architecture> getAll(ObjectContext context) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        return ObjectSelect
                .query(Architecture.class)
                .orderBy(Architecture.CODE.asc())
                .cacheStrategy(QueryCacheStrategy.SHARED_CACHE)
                .select(context);
    }

    public static Architecture getByCode(ObjectContext context, final String code) {
        return tryGetByCode(context, code)
                .orElseThrow(() -> new IllegalStateException("unable to find architecture for code [" + code + "]"));
    }

    public static Optional<Architecture> tryGetByCode(ObjectContext context, final String code) {
        Preconditions.checkNotNull(context, "the context must be provided");
        Preconditions.checkState(!Strings.isNullOrEmpty(code));
        return getAll(context).stream().filter(a -> a.getCode().equals(code)).collect(SingleCollector.optional());
    }

    /**
     * <p>This method will return all of the architectures except for those that are identified by
     * the supplied list of codes.</p>
     */

    public static List<Architecture> getAllExceptByCode(ObjectContext context, final Collection<String> codes) {
        Preconditions.checkArgument(null != context, "the contact must be provided");
        Preconditions.checkArgument(null != codes, "the codes must be provided");
        return getAll(context).stream().filter(a -> !codes.contains(a.getCode())).collect(Collectors.toList());
    }

    UriComponentsBuilder appendPathSegments(UriComponentsBuilder builder) {
        return builder.pathSegment(getCode());
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("code", getCode())
                .build();
    }

}
