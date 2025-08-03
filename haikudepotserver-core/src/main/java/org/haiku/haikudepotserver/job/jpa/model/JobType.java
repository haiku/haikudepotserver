/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.jpa.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;

@Entity
@Table(schema = "job", name = "job_type")
public class JobType {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "job_type_seq")
    @SequenceGenerator(name = "job_type_seq", schema = "job", sequenceName = "job_type_seq", allocationSize = 1)
    private Long id;

    @Column(name = "code", nullable = false, length = 48)
    @Pattern(regexp = "^[a-z0-9]+$")
    private String code;

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
}
