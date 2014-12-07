/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.job.model;

import com.google.common.base.Preconditions;

import java.util.Date;

/**
 * <p>The job run state is used to convey data about the running of a job; has it started, was it cancelled etc...</p>
 */

public class Job implements Comparable<Job> {

    public enum Status {
        INDETERMINATE,
        QUEUED,
        STARTED,
        FINISHED,
        FAILED,
        CANCELLED
    }

    private String ownerUserNickname;
    private Date startTimestamp;
    private Date finishTimestamp;
    private Date queuedTimestamp;
    private Date failTimestamp;
    private Date cancelTimestamp;
    private Integer progressPercent;

    /**
     * <p>This is the {@link org.haikuos.haikudepotserver.support.job.model.JobSpecification} that this instance is
     * conveying the run state for.</p>
     */

    private JobSpecification jobSpecification;

    public Job() {
    }

    public Job(Job other) {
        this();
        Preconditions.checkArgument(null!=other,"the other job run state must be supplied");
        assert other != null;
        this.ownerUserNickname = other.getOwnerUserNickname();
        this.startTimestamp = other.getStartTimestamp();
        this.finishTimestamp = other.getFinishTimestamp();
        this.queuedTimestamp = other.getQueuedTimestamp();
        this.failTimestamp = other.getFailTimestamp();
        this.cancelTimestamp = other.getCancelTimestamp();
        this.progressPercent = other.getProgressPercent();
        this.jobSpecification = other.getJobSpecification();
    }


    public String getOwnerUserNickname() {
        return ownerUserNickname;
    }

    public void setOwnerUserNickname(String ownerUserNickname) {
        this.ownerUserNickname = ownerUserNickname;
    }

    public Date getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(Date startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public void setStartTimestamp() {
        setStartTimestamp(new Date());
    }

    public Date getFinishTimestamp() {
        return finishTimestamp;
    }

    public void setFinishTimestamp(Date finishTimestamp) {
        this.finishTimestamp = finishTimestamp;
    }

    public void setFinishTimestamp() {
        setFinishTimestamp(new Date());
    }

    public Date getQueuedTimestamp() {
        return queuedTimestamp;
    }

    public void setQueuedTimestamp(Date queuedTimestamp) {
        this.queuedTimestamp = queuedTimestamp;
    }

    public void setQueuedTimestamp() {
        setQueuedTimestamp(new Date());
    }

    public Date getFailTimestamp() {
        return failTimestamp;
    }

    public void setFailTimestamp(Date failTimestamp) {
        this.failTimestamp = failTimestamp;
    }

    public void setFailTimestamp() {
        setFailTimestamp(new Date());
    }

    public Date getCancelTimestamp() {
        return cancelTimestamp;
    }

    public void setCancelTimestamp(Date cancelTimestamp) {
        this.cancelTimestamp = cancelTimestamp;
    }

    public void setCancelTimestamp() {
        setCancelTimestamp(new Date());
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public JobSpecification getJobSpecification() {
        return jobSpecification;
    }

    public void setJobSpecification(JobSpecification jobSpecification) {
        this.jobSpecification = jobSpecification;
    }

    public Status getStatus() {

        if(null!=getCancelTimestamp()) {
            return Status.CANCELLED;
        }

        if(null!=getFailTimestamp()) {
            return Status.FAILED;
        }

        if(null!=getFinishTimestamp()) {
            return Status.FINISHED;
        }

        if(null!=getStartTimestamp()) {
            return Status.STARTED;
        }

        if(null!=getQueuedTimestamp()) {
            return Status.QUEUED;
        }

        return Status.INDETERMINATE;
    }

    public boolean isQueuedOrStarted() {
        switch(getStatus()) {
            case QUEUED:
                case STARTED:
                    return true;
        }

        return false;
    }

    public String getJobTypeCode() {
        if(null!=jobSpecification) {
            return jobSpecification.getJobTypeCode();
        }

        return null;
    }

    public String getGuid() {
        if (null != jobSpecification) {
            return jobSpecification.getGuid();
        }

        return null;
    }

    public Long getTimeToLive() {
        if (null != jobSpecification) {
            return jobSpecification.getTimeToLive();
        }

        return null;
    }

    @Override
    public int compareTo(Job o) {
        Date qThis = getQueuedTimestamp();
        Date qOther = o.getQueuedTimestamp();
        int cmp = Long.compare(
                null == qThis ? 0 : qThis.getTime(),
                null == qOther ? 0 : qOther.getTime());

        if(0==cmp) {
            cmp = getGuid().compareTo(o.getGuid());
        }

        return cmp;
    }

    @Override
    public String toString() {
        @SuppressWarnings("StringBufferReplaceableByString")
        StringBuilder result = new StringBuilder();
        result.append("job-run-state ");
        result.append(getGuid());
        result.append(" ");
        result.append(getJobTypeCode());
        result.append(" @ ");
        result.append(getStatus().name());
        return result.toString();
    }

}
