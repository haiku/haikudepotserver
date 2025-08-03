/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.jpa;

import org.haiku.haikudepotserver.job.jpa.model.JobSuppliedData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobSuppliedDataRepository extends JpaRepository<JobSuppliedData, Long> {

    Optional<JobSuppliedData> findByCode(String code);

}
