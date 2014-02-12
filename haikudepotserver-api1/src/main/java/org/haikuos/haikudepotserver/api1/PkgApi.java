/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haikuos.haikudepotserver.api1.model.pkg.*;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;

/**
 * <p>This API is for access to packages and package versions.</p>
 */

@JsonRpcService("/api/v1/pkg")
public interface PkgApi {

    /**
     * <p>This method can be invoked to get a list of all of the packages that match some search critera in the
     * request.</p>
     */

    SearchPkgsResult searchPkgs(SearchPkgsRequest request);

    /**
     * <p>This method will return a package and the specified versions.  It will throw an
     * {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException} if the package was not able to be located.</p>
     */

    GetPkgResult getPkg(GetPkgRequest request) throws ObjectNotFoundException;

    /**
     * <p>This request will remove any icons from the package.</p>
     */

    RemovePkgIconResult removePkgIcon(RemovePkgIconRequest request) throws ObjectNotFoundException;

    /**
     * <p>This method will return an ordered list of the screenshots that are available for this package.  It will
     * throw an {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException} in the case where the
     * nominated package is not able to be found.</p>
     */

    GetPkgScreenshotsResult getPkgScreenshots(GetPkgScreenshotsRequest getPkgScreenshotsRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will remove the nominated screenshot from the package.  If the screenshot is not able to be
     * found using the code supplied, the method will throw an instance of
     * {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException}.</p>
     */

    RemovePkgScreenshotResult removePkgScreenshot(RemovePkgScreenshotRequest removePkgScreenshotRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will reorder the screenshots related to the nominated package.  If any of the screenshots are
     * not accounted for, they will be ordered at the end in an indeterminate manner.  If the package is not able to be
     * found given the name supplied, an instance of
     * {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException} will be thrown.</p>
     */

    ReorderPkgScreenshotsResult reorderPkgScreenshots(ReorderPkgScreenshotsRequest reorderPkgScreenshotsRequest) throws ObjectNotFoundException;

}
