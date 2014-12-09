/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.job;

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

}
