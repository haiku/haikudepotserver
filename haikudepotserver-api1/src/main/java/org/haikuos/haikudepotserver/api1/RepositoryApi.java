/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haikuos.haikudepotserver.api1.model.repository.SearchRepositoriesRequest;
import org.haikuos.haikudepotserver.api1.model.repository.SearchRepositoriesResult;

@JsonRpcService("/api/v1/repository")
public interface RepositoryApi {

    /**
     * <p>This method will search the repositories according to the supplied criteria and will
     * return a list of those found.  Any user is able to see the repositories.</p>
     */

    SearchRepositoriesResult searchRepositories(SearchRepositoriesRequest searchRepositoriesRequest);

}
