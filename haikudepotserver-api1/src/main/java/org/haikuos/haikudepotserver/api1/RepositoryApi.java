/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haikuos.haikudepotserver.api1.model.repository.*;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;

@JsonRpcService("/api/v1/repository")
public interface RepositoryApi {

    /**
     * <p>This method will search the repositories according to the supplied criteria and will
     * return a list of those found.  Any user is able to see the repositories.</p>
     */

    SearchRepositoriesResult searchRepositories(SearchRepositoriesRequest searchRepositoriesRequest);

    /**
     * <p>This method will return the repository details for the repository identified by the
     * code in the request object.</p>
     */

    GetRepositoryResult getRepository(GetRepositoryRequest getRepositoryRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will update the repository.  As well as the data to update, it also includes a 'filter' that
     * defines the fields that should be updated in this request.</p>
     */

    UpdateRepositoryResult updateRepository(UpdateRepositoryRequest updateRepositoryRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will trigger the import process for a repository.</p>
     */

    TriggerImportRepositoryResult triggerImportRepository(TriggerImportRepositoryRequest triggerImportRepositoryRequest) throws ObjectNotFoundException;

}
