/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.job;

import org.haikuos.haikudepotserver.job.model.JobSpecification;
import org.haikuos.haikudepotserver.job.model.JobRunnerException;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * <p>This concrete subclass of {@link org.haikuos.haikudepotserver.job.JobRunner} is able to
 * provide some implementation; for example, automatically being able to determine the "job type code"
 * of the report based on the class name.</p>
 */

public abstract class AbstractJobRunner<T extends JobSpecification> implements JobRunner<T> {

    private final static String SUFFIX = "JobRunner";

    @Resource
    JobOrchestrationService jobOrchestrationService;

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
