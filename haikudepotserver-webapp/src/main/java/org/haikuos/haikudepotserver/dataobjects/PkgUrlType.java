/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.dataobjects.auto._PkgUrlType;
import org.haikuos.haikudepotserver.dataobjects.support.Coded;
import org.haikuos.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;

public class PkgUrlType extends _PkgUrlType implements Coded {

    public static Optional<PkgUrlType> getByCode(ObjectContext context, String code) {
        Preconditions.checkArgument(null!=context);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "a code is required to get the url type");
        List<PkgUrlType> pkgUrlTypes = (List<PkgUrlType>) context.performQuery(new SelectQuery(
                PkgUrlType.class,
                ExpressionFactory.matchExp(Architecture.CODE_PROPERTY, code)));
        return pkgUrlTypes.stream().collect(SingleCollector.optional());
    }

}
