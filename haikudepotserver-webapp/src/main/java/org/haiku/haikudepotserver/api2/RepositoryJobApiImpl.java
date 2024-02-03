/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.api2.model.QueueRepositoryDumpExportJobResponseEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class RepositoryJobApiImpl extends AbstractApiImpl implements RepositoryJobApi {

    private final RepositoryJobApiService repositoryJobApiService;

    public RepositoryJobApiImpl(RepositoryJobApiService repositoryJobApiService) {
        this.repositoryJobApiService = Preconditions.checkNotNull(repositoryJobApiService);
    }

    @Override
    public ResponseEntity<QueueRepositoryDumpExportJobResponseEnvelope> queueRepositoryDumpExportJob(Object body) {
        return ResponseEntity.ok(
                new QueueRepositoryDumpExportJobResponseEnvelope()
                        .result(repositoryJobApiService.queueRepositoryDumpExportJob()));
    }

}
