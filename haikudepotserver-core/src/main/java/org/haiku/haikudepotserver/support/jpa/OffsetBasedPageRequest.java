/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.jpa;

import com.google.common.base.Preconditions;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class OffsetBasedPageRequest implements Pageable {
    private final long limit;
    private final long offset;
    private final Sort sort;

    public OffsetBasedPageRequest(long limit, long offset,  Sort sort) {
        Preconditions.checkArgument(limit > 0, "limit must be > 0");
        Preconditions.checkArgument(offset >= 0, "offset must be >= 0");
        this.limit = limit;
        this.offset = offset;
        this.sort = sort;
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / limit);
    }

    @Override
    public int getPageSize() {
        return (int) limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetBasedPageRequest(limit, offset + limit, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return new OffsetBasedPageRequest(limit, Math.max(0, offset - limit), sort);
    }

    @Override
    public Pageable first() {
        return new OffsetBasedPageRequest(limit, 0, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetBasedPageRequest(limit, Math.max(0, pageNumber) * limit, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}