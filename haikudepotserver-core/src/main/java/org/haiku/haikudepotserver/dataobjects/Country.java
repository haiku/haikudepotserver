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
import org.haiku.haikudepotserver.dataobjects.auto._Country;
import org.haiku.haikudepotserver.dataobjects.support.Coded;
import org.haiku.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;

public class Country extends _Country implements Coded, CreateAndModifyTimestamped {

    public final static String CODE_NZ = "NZ";

    private static final long serialVersionUID = 1L;

    public static List<Country> getAll(ObjectContext context) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        return ObjectSelect
                .query(Country.class)
                .orderBy(Country.NAME.asc())
                .cacheStrategy(QueryCacheStrategy.SHARED_CACHE)
                .select(context);
    }

    public static Country getByCode(ObjectContext context, final String code) {
        return tryGetByCode(context, code)
                .orElseThrow(() -> new IllegalStateException("unable to find country for code [" + code + "]"));
    }

    public static Optional<Country> tryGetByCode(ObjectContext context, final String code) {
        Preconditions.checkNotNull(context, "the context must be provided");
        Preconditions.checkState(!Strings.isNullOrEmpty(code));
        return getAll(context).stream().filter(a -> a.getCode().equals(code)).collect(SingleCollector.optional());
    }

}
