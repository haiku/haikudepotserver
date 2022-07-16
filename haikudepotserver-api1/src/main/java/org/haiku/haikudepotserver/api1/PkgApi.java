/*
 * Copyright 2013-2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgChangelogRequest;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgChangelogResult;
import org.haiku.haikudepotserver.api1.model.pkg.IncrementViewCounterRequest;
import org.haiku.haikudepotserver.api1.model.pkg.IncrementViewCounterResult;

/**
 * <p>This API is for access to packages and package versions.</p>
 */

@Deprecated
@JsonRpcService("/__api/v1/pkg")
public interface PkgApi {

    /**
     * <p>The package might have a change log associated with it.  This is just a long string with notes
     * about what versions were released and what changed in those releases.  If there is no change log
     * stored for this package, a NULL value may be returned in {@link GetPkgChangelogResult#content}.
     * </p>
     */

    @Deprecated
    GetPkgChangelogResult getPkgChangelog(GetPkgChangelogRequest request);

    /**
     * <p>This API will increment the view counter on a PkgVersion.</p>
     */

    @Deprecated
    IncrementViewCounterResult incrementViewCounter(IncrementViewCounterRequest request);

}
