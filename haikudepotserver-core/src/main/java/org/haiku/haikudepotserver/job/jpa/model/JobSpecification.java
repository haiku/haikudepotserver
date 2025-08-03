/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.jpa.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(schema = "job", name = "job_specification")
public class JobSpecification {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "job_specification_seq")
    @SequenceGenerator(name = "job_specification_seq", schema = "job", sequenceName = "job_specification_seq", allocationSize = 1)
    private Long id;

    @Column(name = "create_timestamp", nullable = false)
    private Instant createTimestamp;

    @Column(name = "modify_timestamp", nullable = false)
    private Instant modifyTimestamp;

    @Column(name = "data", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode data;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
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

    @PrePersist
    void addTimestamp() {
        if (null == getCreateTimestamp()) {
            setCreateTimestamp(Instant.now());
        }
        setModifyTimestamp(Instant.now());
    }
}
