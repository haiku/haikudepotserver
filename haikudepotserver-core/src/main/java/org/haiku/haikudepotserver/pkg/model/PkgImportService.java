/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;

public interface PkgImportService {


    /**
     * <p>This method will import the package described by the 'pkg' parameter by locating the package and
     * either creating it or updating it as necessary.</p>
     * @param pkg imports into the local database from this package model.
     * @param repositorySourceObjectId the {@link ObjectId} of the source of the package data.
     * @param populatePayloadLength is able to signal to the import process that the length of the package should be
     *                              populated.
     */

    void importFrom(
            ObjectContext objectContext,
            ObjectId repositorySourceObjectId,
            org.haiku.pkg.model.Pkg pkg,
            boolean populatePayloadLength);

}
