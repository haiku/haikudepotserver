/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

import org.haikuos.haikudepotserver.api1.model.PkgVersionType;

public class GetPkgRequest {

    /**
     * <p>This code defines the repository that you would like to find the package in.  If no such repository
     * is specified then it will default to "haikuports".</p>
     * @since 2015-05-26
     */

    public String repositoryCode;

    /**
     * <p>This is the name of the package that you wish to obtain.</p>
     */

    public String name;

    /**
     * <p>If this is true then the counter on the version is incremented; indicating that the package has been
     * seen.  Do not use this unless the user is being displayed a user-interface of the package so that they
     * have <em>really</em> seen it.  This value may be supplied as null.  This only applies when the
     * {@link org.haikuos.haikudepotserver.api1.model.PkgVersionType#LATEST} version type is
     * being requested.  Also note that the system has a feature to avoid double counting from the same address in
     * quick succession.</p>
     */

    public Boolean incrementViewCounter;

    /**
     * <P>Only a version of the package for this architecture will be returned.  Note that this also
     * includes the pseudo-architectures "any" and "source".</P>
     */

    public String architectureCode;

    public PkgVersionType versionType;

    public String naturalLanguageCode;

    // version coordinates.

    public String major;

    public String minor;

    public String micro;

    public String preRelease;

    public Integer revision;

}
