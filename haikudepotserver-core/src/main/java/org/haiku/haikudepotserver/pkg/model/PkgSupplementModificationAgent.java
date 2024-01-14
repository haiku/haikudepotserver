/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.pkg.model;

import org.haiku.haikudepotserver.dataobjects.User;

/**
 * <p>This model is used to describe a user who is modifying the data on a package
 * supplement. The model is in-memory only.</p>
 */

public interface PkgSupplementModificationAgent {

    String HDS_ORIGIN_SYSTEM_DESCRIPTION = "hds";
    String HDS_HPKG_ORIGIN_SYSTEM_DESCRIPTION = "hds-hpkg";

    public User getUser();

    public String getUserDescription();

    public String getOriginSystemDescription();

}
