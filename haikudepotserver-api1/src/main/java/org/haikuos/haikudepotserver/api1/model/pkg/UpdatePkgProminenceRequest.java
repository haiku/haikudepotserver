/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

public class UpdatePkgProminenceRequest {

    public String pkgName;

    /**
     * <p>This update will occur to the nominated package in relation only to
     * this repository.</p>
     * @since 2015-05-27
     */
    public String repositoryCode;

    public Integer prominenceOrdering;

}
