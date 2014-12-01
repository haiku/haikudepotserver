/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.job;

import org.haikuos.haikudepotserver.support.job.model.JobSpecification;

import javax.annotation.Resource;

/**
 * <p>This concrete subclass of {@link org.haikuos.haikudepotserver.support.job.JobRunner} is able to
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
    public abstract void run(T specification);

}
