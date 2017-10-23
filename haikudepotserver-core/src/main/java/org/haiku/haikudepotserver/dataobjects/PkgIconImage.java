/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.auto._PkgIconImage;

import java.util.List;

public class PkgIconImage extends _PkgIconImage {

    public static List<PkgIconImage> findForPkg(ObjectContext context, Pkg pkg) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != pkg, "the package must be provided");

        return ObjectSelect.query(PkgIconImage.class)
                .where(PKG_ICON.dot(PkgIcon.PKG).eq(pkg))
                .sharedCache()
                .cacheGroup(HaikuDepot.CacheGroup.PKG_ICON.name())
                .select(context);
    }

}
