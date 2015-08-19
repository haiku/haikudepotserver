/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job;

import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobSpecification;

import java.io.IOException;

/**
 * <p>This concrete subclass of {@link JobRunner} is able to
 * provide some implementation; for example, automatically being able to determine the "job type code"
 * of the report based on the class name.</p>
 */

public abstract class AbstractJobRunner<T extends JobSpecification> implements JobRunner<T> {

    private final static String SUFFIX = "JobRunner";

    /**
     * <p>This string is inserted into a cell in order to indicate that the combination of the
     * row and column are true.</p>
     */

    protected static String MARKER = "*";

    @Override
    public String getJobTypeCode() {
        String sn = this.getClass().getSimpleName();

        if(!sn.endsWith(SUFFIX)) {
            throw new IllegalStateException("malformed job runner concrete class; " + sn);
        }

        return sn.substring(0,sn.length() - SUFFIX.length()).toLowerCase();

    }

    @Override
    public abstract void run(JobOrchestrationService jobOrchestrationService, T specification)
            throws IOException, JobRunnerException;

}
