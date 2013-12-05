/*
 * Copyright 2013, Andrew Lindesay
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

    RemoveIconResult removeIcon(RemoveIconRequest request) throws ObjectNotFoundException;

}
