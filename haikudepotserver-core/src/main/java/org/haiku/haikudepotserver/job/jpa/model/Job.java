/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.jpa.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

@Entity
@Table(schema = "job", name = "job")
public class Job {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "job_seq")
    @SequenceGenerator(name = "job_seq", schema = "job", sequenceName = "job_seq", allocationSize = 1)
    private Long id;

    @Column(name = "code", nullable = false)
    @Pattern(regexp = "^[a-z0-9-]+$")
    private String code;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_state_id")
    private JobState state;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_type_id")
    private JobType type;

    @Column(name = "expiry_timestamp", nullable = false)
    private Instant expiryTimestamp;

    @Column(name = "create_timestamp", nullable = false)
    private Instant createTimestamp;

    @Column(name = "modify_timestamp", nullable = false)
    private Instant modifyTimestamp;

    @Column(name = "owner_user_nickname")
    private String ownerUserNickname;

    @OneToMany(mappedBy = "job")
    private List<JobSuppliedData> suppliedDatas;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_specification_id")
    private JobSpecification specification;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public JobType getType() {
        return type;
    }

    public void setType(JobType jobType) {
        this.type = jobType;
    }

    public Instant getExpiryTimestamp() {
        return expiryTimestamp;
    }

    public void setExpiryTimestamp(Instant expiryTimestamp) {
        this.expiryTimestamp = expiryTimestamp;
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

    public String getOwnerUserNickname() {
        return ownerUserNickname;
    }

    public void setOwnerUserNickname(String ownerUserNickname) {
        this.ownerUserNickname = ownerUserNickname;
    }

    public List<JobSuppliedData> getSuppliedDatas() {
        return suppliedDatas;
    }

    public void setSuppliedDatas(List<JobSuppliedData> suppliedDatas) {
        this.suppliedDatas = suppliedDatas;
    }

    public JobState getState() {
        return state;
    }

    public void setState(JobState state) {
        this.state = state;
    }

    public JobSpecification getSpecification() {
        return specification;
    }

    public void setSpecification(JobSpecification specification) {
        this.specification = specification;
    }

    @PrePersist
    void addTimestamp() {
        if (null == getCreateTimestamp()) {
            setCreateTimestamp(Instant.now());
        }
        setModifyTimestamp(Instant.now());
    }
}
