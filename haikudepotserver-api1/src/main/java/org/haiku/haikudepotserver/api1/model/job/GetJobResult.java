/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.job;

import java.util.List;

public class GetJobResult {

    public String guid;
    public JobStatus jobStatus;
    public String jobTypeCode;
    public String ownerUserNickname;
    public Long startTimestamp;
    public Long finishTimestamp;
    public Long queuedTimestamp;
    public Long failTimestamp;
    public Long cancelTimestamp;
    public Integer progressPercent;
    public List<JobData> generatedDatas;

    public static class JobData {

        public String useCode;
        public String guid;
        public String mediaTypeCode;
        public String filename;

    }

}
