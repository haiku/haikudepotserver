/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.pkg;

public class RemovePkgIconRequest {

    /**
     * <p>This is the name of the package that you wish to reset the icon for.</p>
     */

    public String pkgName;

    public RemovePkgIconRequest() {
    }

    public RemovePkgIconRequest(String pkgName) {

        if(null==pkgName || 0==pkgName.length()) {
            throw new IllegalArgumentException("the package name must be supplied when removing the icon for a package");
        }

        this.pkgName = pkgName;
    }
}
