/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.dataobjects;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.SortOrder;
import org.haiku.haikudepotserver.dataobjects.auto._PkgSupplementModification;
import org.haiku.haikudepotserver.dataobjects.support.MutableCreateAndModifyTimestamped;

import java.util.List;

public class PkgSupplementModification extends _PkgSupplementModification implements MutableCreateAndModifyTimestamped {

    private static final long serialVersionUID = 1L;

    public static List<PkgSupplementModification> findForPkg(ObjectContext context, Pkg pkg) {
        return ObjectSelect.query(PkgSupplementModification.class)
                .where(PkgSupplementModification.PKG_SUPPLEMENT.eq(pkg.getPkgSupplement()))
                .orderBy(PkgSupplementModification.CREATE_TIMESTAMP.getName(), SortOrder.ASCENDING)
                .select(context);
    }

}
