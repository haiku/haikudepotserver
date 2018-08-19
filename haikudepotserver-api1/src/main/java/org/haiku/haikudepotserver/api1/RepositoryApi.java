/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haiku.haikudepotserver.api1.model.repository.*;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;

@JsonRpcService("/__api/v1/repository")
public interface RepositoryApi {

    /**
     * <p>This method will return a list of all of the repositories which exist in the system.
     * Minimal data is returned and it is expected that the caller will use the
     * {@link #getRepository(GetRepositoryRequest)} to get specific details for repositories
     * for which finer detail is required.</p>
     */

    GetRepositoriesResult getRepositories(GetRepositoriesRequest getRepositoriesRequest);

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

    /**
     * <p>This method will create a repository.  This method will throw
     * {@link ObjectNotFoundException} if the architecture identified by a
     * supplied code is not able to be found as an architecture.</p>
     */

    CreateRepositoryResult createRepository(CreateRepositoryRequest createRepositoryRequest) throws ObjectNotFoundException;

    /**
     * <p>Returns details of the repository source.</p>
     * @throws ObjectNotFoundException is the repository source is not available.
     */

    GetRepositorySourceResult getRepositorySource(GetRepositorySourceRequest request) throws ObjectNotFoundException;

    /**
     * <p>Allows the repository source to be updated.</p>
     * @throws ObjectNotFoundException if the repository source was not able to be found given the code supplied.
     */

    UpdateRepositorySourceResult updateRepositorySource(UpdateRepositorySourceRequest request) throws ObjectNotFoundException;

    /**
     * <p>Creates the repository source.</p>
     * @throws ObjectNotFoundException if the repository is not able to be found.
     */

    CreateRepositorySourceResult createRepositorySource(CreateRepositorySourceRequest request) throws ObjectNotFoundException;

    /**
     * <p>Creates a new mirror for a repository source.</p>
     * @since 2018-07-23
     */

    CreateRepositorySourceMirrorResult createRepositorySourceMirror(CreateRepositorySourceMirrorRequest request) throws ObjectNotFoundException;

    /**
     * <p>Updates an existing mirror.  The mirror should be identified by its code.
     * A number of fields can be supplied to change.  The changes that are applied
     * are determined by a set of update filters that should be supplied with the
     * request.</p>
     * @since 2018-07-24
     */

    UpdateRepositorySourceMirrorResult updateRepositorySourceMirror(UpdateRepositorySourceMirrorRequest request) throws ObjectNotFoundException;

    /**
     * @since 2018-07-29
     */

    GetRepositorySourceMirrorResult getRepositorySourceMirror(GetRepositorySourceMirrorRequest request) throws ObjectNotFoundException;

}
