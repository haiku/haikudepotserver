/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.dataobjects.auto._PkgIconImage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PkgIconImage extends _PkgIconImage {

    public static List<PkgIconImage> findForPkg(ObjectContext context, Pkg pkg) {
        Preconditions.checkArgument(null!=context, "the context must be provided");
        Preconditions.checkArgument(null!=pkg, "the package must be provided");

        SelectQuery query = new SelectQuery(
                PkgIconImage.class,
                ExpressionFactory.matchExp(PkgIconImage.PKG_ICON_PROPERTY + "." + PkgIcon.PKG_PROPERTY, pkg));

        query.setCacheStrategy(QueryCacheStrategy.SHARED_CACHE);
        query.setCacheGroups(HaikuDepot.CacheGroup.PKG_ICON.name());

        return (List<PkgIconImage>) context.performQuery(query);
    }

    public static Optional<PkgIconImage> getForPkg(
            ObjectContext context,
            Pkg pkg,
            final MediaType mediaType,
            final Integer size) {

        Preconditions.checkArgument(null != mediaType, "the media type must be provided");

        return findForPkg(context, pkg).stream().filter(pii ->
                pii.getPkgIcon().getMediaType().equals(mediaType) &&
                Objects.equals(pii.getPkgIcon().getSize(), size)).findFirst();

    }

}
