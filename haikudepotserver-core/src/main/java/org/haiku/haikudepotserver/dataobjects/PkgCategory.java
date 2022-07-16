/*
 * Copyright 2014-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.haiku.haikudepotserver.dataobjects.auto._PkgCategory;
import org.haiku.haikudepotserver.dataobjects.support.Coded;
import org.haiku.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PkgCategory extends _PkgCategory implements Coded, CreateAndModifyTimestamped {

    public static List<PkgCategory> getAll(ObjectContext context) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        return ObjectSelect.query(PkgCategory.class).sharedCache().orderBy(NAME.asc()).select(context);
    }

    public static PkgCategory getByCode(ObjectContext context, final String code) {
        return tryGetByCode(context, code)
                .orElseThrow(() -> new ObjectNotFoundException(PkgCategory.class.getSimpleName(), code));
    }

    public static Optional<PkgCategory> tryGetByCode(ObjectContext context, final String code) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "the code must be supplied");
        return getAll(context).stream().filter(pc -> pc.getCode().equals(code)).collect(SingleCollector.optional());
    }

    /**
     * <p>Given the codes supplied, this method will return all of the categories for
     * those codes.</p>
     */

    public static List<PkgCategory> getByCodes(ObjectContext context, final Collection<String> codes) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != codes, "the codes must be provided");
        return getAll(context).stream().filter(pc -> codes.contains(pc.getCode())).collect(Collectors.toList());
    }

    /**
     * <p>Can be looked up in the localizations to get a title for this category.</p>
     */

    public String getTitleKey() {
        return "pkgCategory." + getCode().toLowerCase() + ".title";
    }

}
