/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.jpa.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

@Entity
@Table(schema = "job", name = "job_generated_data")
public class JobGeneratedData {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "job_generated_data_seq")
    @SequenceGenerator(name = "job_generated_data_seq", schema = "job", sequenceName = "job_generated_data_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_state_id")
    private JobState jobState;

    @Column(name = "code", nullable = false, length = 48)
    @Pattern(regexp = "^[a-z0-9-]+$")
    private String code;

    @Column(name = "use_code", nullable = false)
    private String useCode;

    @Column(name = "storage_code", nullable = false)
    @Pattern(regexp = "^[a-z0-9-]+$")
    private String storageCode;

    @Column(name = "create_timestamp", nullable = false)
    private Instant createTimestamp;

    @Column(name = "modify_timestamp", nullable = false)
    private Instant modifyTimestamp;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_data_encoding_id")
    private JobDataEncoding encoding;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_data_media_type_id")
    private JobDataMediaType mediaType;

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

    public String getUseCode() {
        return useCode;
    }

    public void setUseCode(String useCode) {
        this.useCode = useCode;
    }

    public String getStorageCode() {
        return storageCode;
    }

    public void setStorageCode(String storageCode) {
        this.storageCode = storageCode;
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

    public JobDataEncoding getEncoding() {
        return encoding;
    }

    public void setEncoding(JobDataEncoding jobDataEncoding) {
        this.encoding = jobDataEncoding;
    }

    public JobDataMediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(JobDataMediaType jobDataMediaType) {
        this.mediaType = jobDataMediaType;
    }

    public JobState getJobState() {
        return jobState;
    }

    public void setJobState(JobState jobState) {
        this.jobState = jobState;
    }

    @PrePersist
    void addTimestamp() {
        if (null == getCreateTimestamp()) {
            setCreateTimestamp(Instant.now());
        }
        setModifyTimestamp(Instant.now());
    }
}