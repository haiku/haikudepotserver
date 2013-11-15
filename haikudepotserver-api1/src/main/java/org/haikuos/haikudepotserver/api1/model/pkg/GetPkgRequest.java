/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

public class GetPkgRequest {

    /**
     * <p>This type defines the versions that should be sent back in the result.  If the client were
     * only interested in the latest version for example, then it should use the "LATEST" value.</p>
     */

    public enum VersionType {
        LATEST
    }

    /**
     * <p>This is the name of the package that you wish to obtain.</p>
     */

    public String name;

    /**
     * <P>Only a version of the package for this architecture will be returned.  Note that this also
     * includes the pseudo-architectures "any" and "source".</P>
     */

    public String architectureCode;

    public VersionType versionType;

    // TODO - natural language

}
