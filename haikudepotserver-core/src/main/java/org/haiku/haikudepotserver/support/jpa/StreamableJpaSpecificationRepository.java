/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.jpa;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.stream.Stream;

/**
 * <p>This interface allows for sub-interfaces of
 * {@link org.springframework.data.jpa.repository.JpaRepository}
 * to be able to stream data.</p>
 */

public interface StreamableJpaSpecificationRepository<T> {

    Stream<T> stream(Specification<T> specification, Class<T> klass, Sort sort);

}
