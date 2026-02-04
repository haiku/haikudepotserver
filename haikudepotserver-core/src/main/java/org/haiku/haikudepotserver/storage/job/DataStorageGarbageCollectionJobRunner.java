/*
 * Copyright 2025-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.storage.job;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.storage.model.DataStorageGarbageCollectionJobSpecification;
import org.haiku.haikudepotserver.storage.model.DataStorageInUseChecker;
import org.haiku.haikudepotserver.storage.model.DataStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * <p>This job will ensure that the stored data is actually used; if not then the
 * data will be deleted.</p>
 */
@Component
public class DataStorageGarbageCollectionJobRunner extends AbstractJobRunner<DataStorageGarbageCollectionJobSpecification> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(DataStorageGarbageCollectionJobRunner.class);

    private final DataStorageService dataStorageService;

    private final List<DataStorageInUseChecker> dataStorageInUseCheckers;

    public DataStorageGarbageCollectionJobRunner(
            DataStorageService dataStorageService,
            List<DataStorageInUseChecker> dataStorageInUseCheckers
    ) {
        this.dataStorageService = dataStorageService;
        this.dataStorageInUseCheckers = dataStorageInUseCheckers;
    }

    @Override
    public Class<DataStorageGarbageCollectionJobSpecification> getSupportedSpecificationClass() {
        return DataStorageGarbageCollectionJobSpecification.class;
    }

    @Override
    public void run(JobService jobService, DataStorageGarbageCollectionJobSpecification specification) throws IOException, JobRunnerException {
        Preconditions.checkNotNull(specification);
        Preconditions.checkNotNull(specification.getOlderThanMillis());
        Preconditions.checkNotNull(jobService);

        Set<String> allCandidateKeysToDelete = dataStorageService.keys(Duration.ofMillis(specification.getOlderThanMillis()));

        LOGGER.info("garbage collection started for {} keys", allCandidateKeysToDelete.size());

        Set<String> filteredKeysToDelete = dataStorageInUseCheckers.stream().reduce(
                allCandidateKeysToDelete,
                (candidateKeysToDelete, dataStorageInUseChecker) -> {
                    Set<String> inUse = dataStorageInUseChecker.inUse(candidateKeysToDelete);
                    return Sets.difference(candidateKeysToDelete, inUse);
                },
                Sets::union
        );

        long deleted = dataStorageService.remove(filteredKeysToDelete);

        LOGGER.info("did delete {} keys out of {}", deleted, allCandidateKeysToDelete.size());
    }

}
