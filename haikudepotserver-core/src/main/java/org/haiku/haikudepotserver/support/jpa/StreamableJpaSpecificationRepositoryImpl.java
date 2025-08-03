/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.jpa;

import com.google.common.base.Preconditions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

@Component
public class StreamableJpaSpecificationRepositoryImpl<T> implements StreamableJpaSpecificationRepository<T> {

    private EntityManager entityManager;

    public StreamableJpaSpecificationRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Stream<T> stream(Specification<T> specification, Class<T> klass, Sort sort) {
        Preconditions.checkArgument(null != specification);
        Preconditions.checkArgument(null != klass);

        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> criteriaQuery = criteriaBuilder.createQuery(klass);
        Root<T> root = criteriaQuery.from(klass);

        criteriaQuery.select(root);
        criteriaQuery.where(specification.toPredicate(root, criteriaQuery, criteriaBuilder));

        if (null != sort) {
            List<Order> jpaOrders = QueryUtils.toOrders(sort, root, criteriaBuilder);
            criteriaQuery.orderBy(jpaOrders);
        }

        return entityManager.createQuery(criteriaQuery).getResultStream();
    }

}
