/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.api2.model.CreateRepositoryRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateRepositoryResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateRepositorySourceMirrorRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateRepositorySourceMirrorResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateRepositorySourceRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateRepositorySourceResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositoriesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositoriesResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositoryRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositoryResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositorySourceMirrorRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositorySourceMirrorResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositorySourceRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositorySourceResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.RemoveRepositorySourceMirrorRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.RemoveRepositorySourceMirrorResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchRepositoriesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchRepositoriesResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.TriggerImportRepositoryRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.TriggerImportRepositoryResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateRepositoryRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateRepositoryResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateRepositorySourceMirrorRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateRepositorySourceMirrorResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateRepositorySourceRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateRepositorySourceResponseEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class RepositoryApiImpl extends AbstractApiImpl implements RepositoryApi {

    private final RepositoryApiService repositoryApiService;

    public RepositoryApiImpl(RepositoryApiService repositoryApiService) {
        this.repositoryApiService = Preconditions.checkNotNull(repositoryApiService);
    }

    @Override
    public ResponseEntity<CreateRepositoryResponseEnvelope> createRepository(CreateRepositoryRequestEnvelope createRepositoryRequestEnvelope) {
        repositoryApiService.createRepository(createRepositoryRequestEnvelope);
        return ResponseEntity.ok(new CreateRepositoryResponseEnvelope().result(Map.of()));

    }

    @Override
    public ResponseEntity<CreateRepositorySourceResponseEnvelope> createRepositorySource(CreateRepositorySourceRequestEnvelope createRepositorySourceRequestEnvelope) {
        repositoryApiService.createRepositorySource(createRepositorySourceRequestEnvelope);
        return ResponseEntity.ok(new CreateRepositorySourceResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<CreateRepositorySourceMirrorResponseEnvelope> createRepositorySourceMirror(CreateRepositorySourceMirrorRequestEnvelope createRepositorySourceMirrorRequestEnvelope) {
        return ResponseEntity.ok(
                new CreateRepositorySourceMirrorResponseEnvelope()
                        .result(repositoryApiService.createRepositorySourceMirror(createRepositorySourceMirrorRequestEnvelope)));
    }

    @Override
    public ResponseEntity<GetRepositoriesResponseEnvelope> getRepositories(GetRepositoriesRequestEnvelope getRepositoriesRequestEnvelope) {
        return ResponseEntity.ok(
                new GetRepositoriesResponseEnvelope()
                        .result(repositoryApiService.getRepositories(getRepositoriesRequestEnvelope)));
    }

    @Override
    public ResponseEntity<GetRepositoryResponseEnvelope> getRepository(GetRepositoryRequestEnvelope getRepositoryRequestEnvelope) {
        return ResponseEntity.ok(
                new GetRepositoryResponseEnvelope()
                        .result(repositoryApiService.getRepository(getRepositoryRequestEnvelope)));
    }

    @Override
    public ResponseEntity<GetRepositorySourceResponseEnvelope> getRepositorySource(GetRepositorySourceRequestEnvelope getRepositorySourceRequestEnvelope) {
        return ResponseEntity.ok(
                new GetRepositorySourceResponseEnvelope()
                        .result(repositoryApiService.getRepositorySource(getRepositorySourceRequestEnvelope)));
    }

    @Override
    public ResponseEntity<GetRepositorySourceMirrorResponseEnvelope> getRepositorySourceMirror(GetRepositorySourceMirrorRequestEnvelope getRepositorySourceMirrorRequestEnvelope) {
        return ResponseEntity.ok(
                new GetRepositorySourceMirrorResponseEnvelope()
                        .result(repositoryApiService.getRepositorySourceMirror(getRepositorySourceMirrorRequestEnvelope)));
    }

    @Override
    public ResponseEntity<RemoveRepositorySourceMirrorResponseEnvelope> removeRepositorySourceMirror(RemoveRepositorySourceMirrorRequestEnvelope removeRepositorySourceMirrorRequestEnvelope) {
        repositoryApiService.removeRepositorySourceMirror(removeRepositorySourceMirrorRequestEnvelope);
        return ResponseEntity.ok(new RemoveRepositorySourceMirrorResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<SearchRepositoriesResponseEnvelope> searchRepositories(SearchRepositoriesRequestEnvelope searchRepositoriesRequestEnvelope) {
        return ResponseEntity.ok(
                new SearchRepositoriesResponseEnvelope()
                        .result(repositoryApiService.searchRepositories(searchRepositoriesRequestEnvelope)));

    }

    @Override
    public ResponseEntity<TriggerImportRepositoryResponseEnvelope> triggerImportRepository(TriggerImportRepositoryRequestEnvelope triggerImportRepositoryRequestEnvelope) {
        repositoryApiService.triggerImportRepository(triggerImportRepositoryRequestEnvelope);
        return ResponseEntity.ok(new TriggerImportRepositoryResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<UpdateRepositoryResponseEnvelope> updateRepository(UpdateRepositoryRequestEnvelope updateRepositoryRequestEnvelope) {
        repositoryApiService.updateRepository(updateRepositoryRequestEnvelope);
        return ResponseEntity.ok(new UpdateRepositoryResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<UpdateRepositorySourceResponseEnvelope> updateRepositorySource(UpdateRepositorySourceRequestEnvelope updateRepositorySourceRequestEnvelope) {
        repositoryApiService.updateRepositorySource(updateRepositorySourceRequestEnvelope);
        return ResponseEntity.ok(new UpdateRepositorySourceResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<UpdateRepositorySourceMirrorResponseEnvelope> updateRepositorySourceMirror(UpdateRepositorySourceMirrorRequestEnvelope updateRepositorySourceMirrorRequestEnvelope) {
        repositoryApiService.updateRepositorySourceMirror(updateRepositorySourceMirrorRequestEnvelope);
        return ResponseEntity.ok(new UpdateRepositorySourceMirrorResponseEnvelope().result(Map.of()));
    }
}
