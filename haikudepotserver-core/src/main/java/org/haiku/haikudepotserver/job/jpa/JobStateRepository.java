/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.jpa;

import org.haiku.haikudepotserver.job.jpa.model.JobState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface JobStateRepository extends JpaRepository<JobState, Long> {

    @Query(value = "SELECT j.state FROM Job j WHERE j.code = ?1")
    Optional<JobState> findByJobCode(String jobCode);

}
