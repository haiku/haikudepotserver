/*
 * Copyright 2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.SortOrder;
import org.haiku.haikudepotserver.dataobjects.auto._UserUsageConditions;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;

public class UserUsageConditions extends _UserUsageConditions {

    private static final long serialVersionUID = 1L;

    public static UserUsageConditions getLatest(ObjectContext context) {
        return tryGetLatest(context).orElseThrow(
                () -> new IllegalStateException("unable to find the latest user usage conditions"));
    }

    private static Optional<UserUsageConditions> tryGetLatest(ObjectContext context) {
        return Optional.ofNullable(ObjectSelect.query(UserUsageConditions.class)
                .sharedCache()
                .cacheGroup(HaikuDepot.CacheGroup.USER_USAGE_CONDITIONS.name())
                .orderBy(UserUsageConditions.ORDERING.getName(), SortOrder.DESCENDING)
                .limit(1)
                .selectOne(context));
    }

    public static UserUsageConditions getByCode(ObjectContext context, String code) {
        return tryGetByCode(context, code).orElseThrow(
                () -> new IllegalStateException("unable to find the user usage conditions [" + code + "]"));
    }

    public static Optional<UserUsageConditions> tryGetByCode(ObjectContext context, String code) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "a code is required to get the user usage conditions");
        return getAll(context).stream().filter(put -> put.getCode().equals(code)).collect(SingleCollector.optional());
    }

    public static List<UserUsageConditions> getAll(ObjectContext context) {
        return ObjectSelect.query(UserUsageConditions.class)
                .sharedCache()
                .cacheGroup(HaikuDepot.CacheGroup.USER_USAGE_CONDITIONS.name())
                .orderBy(UserUsageConditions.ORDERING.getName(), SortOrder.DESCENDING)
                .select(context);
    }

}
