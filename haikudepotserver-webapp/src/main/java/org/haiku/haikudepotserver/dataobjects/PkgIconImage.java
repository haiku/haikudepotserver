/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.apache.cayenne.query.SelectQuery;
import org.haiku.haikudepotserver.dataobjects.auto._PkgIconImage;

import java.util.List;

public class PkgIconImage extends _PkgIconImage {

    public static List<PkgIconImage> findForPkg(ObjectContext context, Pkg pkg) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != pkg, "the package must be provided");

        SelectQuery query = new SelectQuery(
                PkgIconImage.class,
                ExpressionFactory.matchExp(PkgIconImage.PKG_ICON_PROPERTY + "." + PkgIcon.PKG_PROPERTY, pkg));

        query.setCacheStrategy(QueryCacheStrategy.SHARED_CACHE);
        query.setCacheGroups(HaikuDepot.CacheGroup.PKG_ICON.name());

        return (List<PkgIconImage>) context.performQuery(query);
    }

}
