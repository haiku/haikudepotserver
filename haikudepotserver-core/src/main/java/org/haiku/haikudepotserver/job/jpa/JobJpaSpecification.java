/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.jpa;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;
import org.haiku.haikudepotserver.job.jpa.model.*;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

// Beware that there's a clash in the terminology of the JPA "specification" and the `JobSpecification`
// class! This one is the latter.

public class JobJpaSpecification {

    private final static Set<JobSnapshot.Status> STATUSES_COMPLETED = Set.of(
            JobSnapshot.Status.FINISHED, JobSnapshot.Status.FAILED, JobSnapshot.Status.CANCELLED
    );

    static Specification<Job> any() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and();
    }

    static Specification<Job> notHasStatus(Set<JobSnapshot.Status> statuses) {
        return (root, query, criteriaBuilder)
                -> criteriaBuilder.not(createStatusesPredicate(root, criteriaBuilder, statuses));
    }

    static Specification<Job> hasStatus(Set<JobSnapshot.Status> statuses) {
        return (root, query, criteriaBuilder)
                -> createStatusesPredicate(root, criteriaBuilder, statuses);
    }

    static Specification<Job> hasCompletedStatus() {
        return hasStatus(STATUSES_COMPLETED);
    }

    static Specification<Job> type(String jobTypeCode) {
        return (root, query, criteriaBuilder)
                -> criteriaBuilder.equal(root.get(Job_.type).get(JobType_.code), jobTypeCode);
    }

    static Specification<Job> code(String jobCode) {
        return (root, query, criteriaBuilder)
                -> criteriaBuilder.equal(root.get(Job_.code), jobCode);
    }

    static Specification<Job> ownerUserNickname(String ownerUserNickname) {
        return (root, query, criteriaBuilder)
                -> criteriaBuilder.equal(root.get(Job_.ownerUserNickname), ownerUserNickname);
    }

    static Specification<Job> expiredBeforeTimestamp(Instant expiryCutoff) {
        return (root, query, criteriaBuilder)
                -> criteriaBuilder.lessThan(root.get(Job_.expiryTimestamp), expiryCutoff);
    }

    static Predicate createStatusTimestampAttributesAreNullPredicate(
            Root<Job> root,
            CriteriaBuilder criteriaBuilder,
            Set<SingularAttribute<? super JobState, Instant>> nullAttributes) {
        return nullAttributes.stream()
                .map(a -> root.get(Job_.state).get(a).isNull())
                .reduce(
                        criteriaBuilder.and(), // true
                        criteriaBuilder::and
                );
    }

    static Predicate createStatusTimestampAttributeIsAndIsNotNullPredicate(
            Root<Job> root,
            CriteriaBuilder criteriaBuilder,
            SingularAttribute<? super JobState, Instant> notNullAttribute,
            Set<SingularAttribute<? super JobState, Instant>> nullAttributes
    ) {
        return criteriaBuilder.and(
                root.get(Job_.state).get(notNullAttribute).isNotNull(),
                createStatusTimestampAttributesAreNullPredicate(root, criteriaBuilder, nullAttributes)
        );
    }

    static Predicate createStatusPredicate(Root<Job> root, CriteriaBuilder criteriaBuilder, JobSnapshot.Status status) {
        return switch (status) {
            case QUEUED -> createStatusTimestampAttributeIsAndIsNotNullPredicate(
                    root,
                    criteriaBuilder,
                    JobState_.queueTimestamp,
                    Set.of(JobState_.startTimestamp, JobState_.failTimestamp, JobState_.cancelTimestamp)
            );
            case STARTED -> createStatusTimestampAttributeIsAndIsNotNullPredicate(
                    root,
                    criteriaBuilder,
                    JobState_.startTimestamp,
                    Set.of(JobState_.failTimestamp, JobState_.cancelTimestamp, JobState_.finishTimestamp)
            );
            case FINISHED -> createStatusTimestampAttributeIsAndIsNotNullPredicate(
                    root,
                    criteriaBuilder,
                    JobState_.finishTimestamp,
                    Set.of(JobState_.failTimestamp, JobState_.cancelTimestamp)
            );
            case FAILED -> createStatusTimestampAttributeIsAndIsNotNullPredicate(
                    root,
                    criteriaBuilder,
                    JobState_.failTimestamp,
                    Set.of(JobState_.cancelTimestamp)
            );
            case CANCELLED -> root.get(Job_.state).get(JobState_.cancelTimestamp).isNotNull();
            case INDETERMINATE -> criteriaBuilder.and();
        };
    }

    static Predicate createStatusesPredicate(
            Root<Job> root,
            CriteriaBuilder criteriaBuilder,
            Collection<JobSnapshot.Status> statuses) {
        if (null == statuses) {
            return criteriaBuilder.and(); // true
        }

        return statuses.stream()
                .map(status -> createStatusPredicate(root, criteriaBuilder, status))
                .reduce(
                        criteriaBuilder.or(), // false
                        criteriaBuilder::or
                );
    }

}
