/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.haiku.haikudepotserver.dataobjects.auto._PkgUrlType;
import org.haiku.haikudepotserver.dataobjects.support.Coded;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;

public class PkgUrlType extends _PkgUrlType implements Coded {

    public static Optional<PkgUrlType> getByCode(ObjectContext context, String code) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "a code is required to get the url type");
        List<PkgUrlType> pkgUrlTypes = (List<PkgUrlType>) context.performQuery(new SelectQuery(
                PkgUrlType.class,
                ExpressionFactory.matchExp(Architecture.CODE_PROPERTY, code)));
        return pkgUrlTypes.stream().collect(SingleCollector.optional());
    }

}
