/*
 * Copyright 2021-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;

public interface PkgImportService {


    /**
     * <p>This method will import the package described by the 'pkg' parameter by locating the package and
     * either creating it or updating it as necessary.</p>
     * @param pkg imports into the local database from this package model.
     * @param repositorySourceObjectId the {@link ObjectId} of the source of the package data.
     * @param populateFromPayload is able to signal to the import process that the length of the package should be
     *                              populated.
     */

    void importFrom(
            ObjectContext objectContext,
            ObjectId repositorySourceObjectId,
            org.haiku.pkg.model.Pkg pkg,
            boolean populateFromPayload);

    boolean shouldPopulateFromPayload(PkgVersion persistedPkgVersion);

    /**
     * <p>This will read in the payload into a temporary file.  From there it will parse it
     * and take up any data from it such as the icon and the length of the download in
     * bytes.</p>
     */

    void populateFromPayload(ObjectContext objectContext, PkgVersion persistedPkgVersion);

}
