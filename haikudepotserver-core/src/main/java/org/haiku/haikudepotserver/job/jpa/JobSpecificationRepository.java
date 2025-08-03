/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.jpa;

import org.haiku.haikudepotserver.job.jpa.model.JobSpecification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobSpecificationRepository extends JpaRepository<JobSpecification, Long>  {
}
