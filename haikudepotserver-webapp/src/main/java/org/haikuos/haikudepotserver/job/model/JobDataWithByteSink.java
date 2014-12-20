/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.job.model;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteSink;

/**
 * <p>Couples a {@link org.haikuos.haikudepotserver.job.model.JobData} with a {@link com.google.common.io.ByteSink}
 * such that data for the job data can be provided by a client.</p>
 */

public class JobDataWithByteSink {

    private JobData jobData;
    private ByteSink byteSink;

    public JobDataWithByteSink(JobData jobData, ByteSink byteSink) {
        Preconditions.checkArgument(null!=jobData, "the job data must be supplied");
        Preconditions.checkArgument(null!=byteSink, "the byte sink must be supplied");
        this.jobData = jobData;
        this.byteSink = byteSink;
    }

    public JobData getJobData() {
        return jobData;
    }

    public ByteSink getByteSink() {
        return byteSink;
    }
}
