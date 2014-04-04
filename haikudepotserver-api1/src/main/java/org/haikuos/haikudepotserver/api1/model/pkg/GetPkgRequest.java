/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

public class GetPkgRequest {

    /**
     * <p>This type defines the versions that should be sent back in the result.  If the client were
     * only interested in the latest version for example, then it should use the "LATEST" value.</p>
     */

    public enum VersionType {
        LATEST,
        NONE
    }

    /**
     * <p>This is the name of the package that you wish to obtain.</p>
     */

    public String name;

    /**
     * <p>If this is true then the counter on the version is incremented; indicating that the package has been
     * seen.  Do not use this unless the user is being displayed a user-interface of the package so that they
     * have <em>really</em> seen it.  This value may be supplied as null.  This only applies when the
     * {@link org.haikuos.haikudepotserver.api1.model.pkg.GetPkgRequest.VersionType#LATEST} version type is
     * being requested.  Also note that the system has a feature to avoid double counting from the same address in
     * quick succession.</p>
     */

    public Boolean incrementViewCounter;

    /**
     * <P>Only a version of the package for this architecture will be returned.  Note that this also
     * includes the pseudo-architectures "any" and "source".</P>
     */

    public String architectureCode;

    public VersionType versionType;

    public String naturalLanguageCode;

}
