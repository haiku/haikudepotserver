/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.pkg;

import java.util.List;

/**
 * @since 2015-11-24
 */

public class UpdatePkgVersionRequest {

    public enum Filter {
        ACTIVE
    }

    // ---------------------
    // attributes used to identify the pkg version to update

    /**
     * @since 2022-03-17
     */
    public String repositorySourceCode;

    public String pkgName;

    public String architectureCode;

    // version coordinates.

    public String major;

    public String minor;

    public String micro;

    public String preRelease;

    public Integer revision;

    // ---------------------
    // attributes for modification

    public Boolean active;

    public List<Filter> filter;

}
