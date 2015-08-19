/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job;

import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;

import java.io.IOException;
import java.util.Optional;

/**
 * <p>This interface is only concerned with the immediate binary data storage of the job data.  This service will be
 * able to abstract the storage of the report data from the
 * {@link JobOrchestrationService}.</p>
 */

interface JobDataStorageService {

    ByteSink put(String guid) throws IOException;

    /**
     * @param guid the identifier for the job data to obtain
     * @return an {@link java.io.OutputStream} that will supply the data or null if the job data was not able to be found.
     * @throws IOException
     */

    Optional<? extends ByteSource> get(String guid) throws IOException;

    boolean remove(String guid);

    void clear();

}
