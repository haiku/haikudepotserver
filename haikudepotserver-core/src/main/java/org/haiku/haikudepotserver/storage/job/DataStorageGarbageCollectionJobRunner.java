/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.storage.job;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.storage.model.DataStorageGarbageCollectionJobSpecification;
import org.haiku.haikudepotserver.storage.model.DataStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>This job will ensure that the stored data is actually used; if not then the
 * data will be deleted.</p>
 */
@Component
public class DataStorageGarbageCollectionJobRunner extends AbstractJobRunner<DataStorageGarbageCollectionJobSpecification> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(DataStorageGarbageCollectionJobRunner.class);

    private final DataStorageService dataStorageService;

    public DataStorageGarbageCollectionJobRunner(DataStorageService dataStorageService) {
        this.dataStorageService = dataStorageService;
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

        Set<String> keys = dataStorageService.keys(Duration.ofMillis(specification.getOlderThanMillis()));

        LOGGER.info("garbage collection started for {} keys", keys.size());

        Set<String> keysToDelete = keys
                .stream()
                .filter(k -> !isInUse(jobService, k))
                .collect(Collectors.toSet());

        for (String keyToDelete : keysToDelete) {
            dataStorageService.remove(keyToDelete);
        }

        LOGGER.info("did delete {} keys", keysToDelete.size());
    }

    /**
     * @return true if the key is in use and so the data stored against the key should
     *  be retained.
     */
    private boolean isInUse(JobService jobService, String key) {

        if (jobService.tryGetJobForData(key).isPresent()) {
            return true;
        }

        if (jobService.tryGetJobForSuppliedData(key).isPresent()) {
            return true;
        }

        return false;
    }

}
