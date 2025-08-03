/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.jpa.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;

@Entity
@Table(schema = "job", name = "job_state")
public class JobState {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "job_state_seq")
    @SequenceGenerator(name = "job_state_seq", schema = "job", sequenceName = "job_state_seq", allocationSize = 1)
    private Long id;

    @OneToMany
    @JoinColumn(name = "job_state_id")
    private List<Job> jobs;

    @Column(name = "start_timestamp")
    private Instant startTimestamp;

    @Column(name = "finish_timestamp")
    private Instant finishTimestamp;

    @Column(name = "queue_timestamp")
    private Instant queueTimestamp;

    @Column(name = "fail_timestamp")
    private Instant failTimestamp;

    @Column(name = "cancel_timestamp")
    private Instant cancelTimestamp;

    @Column(name = "create_timestamp", nullable = false)
    private Instant createTimestamp;

    @Column(name = "modify_timestamp", nullable = false)
    private Instant modifyTimestamp;

    @Column(name = "progress_percent")
    private Integer progressPercent;

    @OneToMany(mappedBy = "jobState")
    private List<JobGeneratedData> generatedDatas;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Job getJob() {
        return switch (jobs.size()) {
            case 0 -> null;
            case 1 -> jobs.getFirst();
            default -> throw new IllegalStateException("more than one job for a job state");
        };
    }

    public List<Job> getJobs() {
        return jobs;
    }

    public void setJobs(List<Job> jobs) {
        this.jobs = jobs;
    }

    public Instant getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(Instant startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public Instant getFinishTimestamp() {
        return finishTimestamp;
    }

    public void setFinishTimestamp(Instant finishTimestamp) {
        this.finishTimestamp = finishTimestamp;
    }

    public Instant getQueueTimestamp() {
        return queueTimestamp;
    }

    public void setQueueTimestamp(Instant queueTimestamp) {
        this.queueTimestamp = queueTimestamp;
    }

    public Instant getFailTimestamp() {
        return failTimestamp;
    }

    public void setFailTimestamp(Instant failTimestamp) {
        this.failTimestamp = failTimestamp;
    }

    public Instant getCancelTimestamp() {
        return cancelTimestamp;
    }

    public void setCancelTimestamp(Instant cancelTimestamp) {
        this.cancelTimestamp = cancelTimestamp;
    }

    public Instant getCreateTimestamp() {
        return createTimestamp;
    }

    public void setCreateTimestamp(Instant createTimestamp) {
        this.createTimestamp = createTimestamp;
    }

    public Instant getModifyTimestamp() {
        return modifyTimestamp;
    }

    public void setModifyTimestamp(Instant modifyTimestamp) {
        this.modifyTimestamp = modifyTimestamp;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public List<JobGeneratedData> getGeneratedDatas() {
        return generatedDatas;
    }

    public void setGeneratedDatas(List<JobGeneratedData> generatedDatas) {
        this.generatedDatas = generatedDatas;
    }

    @PrePersist
    void addTimestamp() {
        if (null == getCreateTimestamp()) {
            setCreateTimestamp(Instant.now());
        }
        setModifyTimestamp(Instant.now());
    }

}
