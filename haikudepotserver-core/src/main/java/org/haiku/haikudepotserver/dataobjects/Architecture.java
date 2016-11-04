/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.haiku.haikudepotserver.dataobjects.auto._Architecture;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Architecture extends _Architecture {

    public final static String CODE_SOURCE = "source";
    public final static String CODE_ANY = "any";
    public final static String CODE_X86 = "x86";

    public static List<Architecture> getAll(ObjectContext context) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        SelectQuery query = new SelectQuery(Architecture.class);
        query.addOrdering(Architecture.CODE_PROPERTY, SortOrder.ASCENDING);
        query.setCacheStrategy(QueryCacheStrategy.SHARED_CACHE);
        return (List<Architecture>) context.performQuery(query);
    }

    public static Optional<Architecture> getByCode(ObjectContext context, final String code) {
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
        return new ToStringBuilder(this)
                .append("code", getCode())
                .build();
    }

}
