/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.jpa;

import org.haiku.haikudepotserver.job.jpa.model.JobDataMediaType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobDataMediaTypeRepository extends JpaRepository<JobDataMediaType, Long> {

    JobDataMediaType getByCode(String code);

}
