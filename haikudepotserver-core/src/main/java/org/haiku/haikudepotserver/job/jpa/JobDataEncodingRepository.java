/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.jpa;

import org.haiku.haikudepotserver.job.jpa.model.JobDataEncoding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobDataEncodingRepository extends JpaRepository<JobDataEncoding, Long> {

    JobDataEncoding getByCode(String code);

}
