/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.jpa;

import org.haiku.haikudepotserver.job.jpa.model.JobGeneratedData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobGeneratedDataRepository extends JpaRepository<JobGeneratedData, Long> {

    Optional<JobGeneratedData> findByCode(String code);

}
