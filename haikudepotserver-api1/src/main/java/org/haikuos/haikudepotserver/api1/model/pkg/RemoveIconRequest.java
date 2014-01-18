/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

public class RemoveIconRequest {

    /**
     * <p>This is the name of the package that you wish to reset the icon for.</p>
     */

    public String name;

    public RemoveIconRequest() {
    }


    public RemoveIconRequest(String name) {

        if(null==name || 0==name.length()) {
            throw new IllegalArgumentException("the name must be supplied when removing the icon for a package");
        }

        this.name = name;
    }
}
