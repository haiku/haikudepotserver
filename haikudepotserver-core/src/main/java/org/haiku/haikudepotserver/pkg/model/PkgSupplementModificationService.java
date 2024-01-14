/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.pkg.model;

import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.dataobjects.PkgSupplementModification;

public interface PkgSupplementModificationService {

    /**
     * This method will add a new record into the table capturing what has changed to the package
     * data.
     */

    PkgSupplementModification appendModification(
            ObjectContext context,
            PkgSupplement pkgSupplement,
            PkgSupplementModificationAgent agent,
            String content);

}
