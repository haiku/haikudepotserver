/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.jpa;

import org.haiku.haikudepotserver.job.jpa.model.Job;
import org.haiku.haikudepotserver.support.jpa.StreamableJpaSpecificationRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends
        StreamableJpaSpecificationRepository<Job>, JpaRepository<Job, Long> {

    Optional<Job> getByCode(String code);

    List<Job> findAll(Specification<Job> jpaSpecification, Pageable page);

    long count(Specification<Job> jpaSpecification);

    boolean existsByCode(String code);

    long deleteByCode(String code);

    void delete(Specification<Job> jpaSpecification);

}
