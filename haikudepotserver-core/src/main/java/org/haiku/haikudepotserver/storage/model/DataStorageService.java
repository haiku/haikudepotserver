/*
 * Copyright 2016-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.storage.model;

import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import org.haiku.haikudepotserver.job.model.JobService;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * <p>This interface is only concerned with the immediate binary data storage of the job data.  This service will be
 * able to abstract the storage of the report data from the
 * {@link JobService}.</p>
 */

public interface DataStorageService {

    /**
     * <p>Returns a set of all the keys to data in the store.</p>
     * @param olderThanDuration only data items older than this will be returned.
     */

    Set<String> keys(Duration olderThanDuration);

    /**
     * <p>Creates a sink to write data to for the supplied key.</p>
     */

    ByteSink put(String key) throws IOException;

    /**
     * @param key the identifier for the job data to obtain
     * @return an {@link java.io.OutputStream} that will supply the data or null if the job data was not able to be found.
     */

    Optional<? extends ByteSource> get(String key) throws IOException;

    boolean remove(String key);

    /**
     * <p>Removes all the items from storage.</p>
     */

    void clear();

}
