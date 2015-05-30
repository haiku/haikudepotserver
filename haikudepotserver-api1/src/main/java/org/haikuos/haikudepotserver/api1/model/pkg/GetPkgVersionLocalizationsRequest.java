/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

import java.util.List;

public class GetPkgVersionLocalizationsRequest {

    public String pkgName;

    /**
     * <p>This repository code identifies a repository from which to get the package version from</p>
     * @since 2015-05-27
     */

    public String repositoryCode;

    public List<String> naturalLanguageCodes;

    /**
     * <P>The architecture is required and is considered if the request is for the latest version of the package
     * or for a specific version.</P>
     */

    public String architectureCode;

    /**
     * <P>If the major value is supplied then the request will be considered to be in the context of a specific
     * package version.  If it is not supplied then the request will be considered to be referring to the
     * latest version of the package.</P>
     */

    public String major;

    public String minor;

    public String micro;

    public String preRelease;

    public Integer revision;

}
