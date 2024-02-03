/*
 * Copyright 2014-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.util.Date;

/**
 * <p>This class models reference to a piece of binary data that is associated with a job.  For example, a
 * "spreadsheet" job may be used to generate a report.  The output of the report would be an instance of
 * {@link JobData}.  The actual payload can be obtained from the
 * instance of {@link JobService} and this is vended to
 * users over the internet from &quot;JobController&quot;.</p>
 */

public class JobData {

    private final JobDataType dataType;
    private final String useCode;
    private final String guid;
    private final String mediaTypeCode;
    private final JobDataEncoding encoding;
    private final Date createTimestamp;

    public JobData(String guid, JobDataType dataType, String useCode, String mediaTypeCode, JobDataEncoding encoding) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(guid), "the guid must be supplied to identify the data");
        Preconditions.checkArgument(null != dataType, "the data type must be provided");
        Preconditions.checkArgument(null != encoding, "the encoding must be provided");
        this.guid = guid;
        this.dataType = dataType;
        this.useCode = useCode;
        this.mediaTypeCode = mediaTypeCode;
        this.encoding = encoding;
        this.createTimestamp = new Date();
    }

    public String getUseCode() {
        return useCode;
    }

    public String getGuid() {
        return guid;
    }

    public String getMediaTypeCode() {
        return mediaTypeCode;
    }

    public JobDataEncoding getEncoding() {
        return encoding;
    }

    public JobDataType getDataType() {
        return dataType;
    }

    public Date getCreateTimestamp() {
        return createTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobData jobData = (JobData) o;
        return guid.equals(jobData.guid);
    }

    @Override
    public int hashCode() {
        return guid.hashCode();
    }

    @Override
    public String toString() {
        return "job-data; " + getGuid();
    }

}
