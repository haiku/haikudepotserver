/*
 * Copyright 2014-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.model;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.stream.IntStream;

/**
 * <P>This object aims to provide the pagination within a list of items.  It aims to be more or less
 * like the "paginationcontroldirective.js" behaviour.</P>
 * @param total is the total number of items in the entire result set
 * @param offset if the offset from 0 into the total number of items
 * @param pageSize is the maximum number of items to show on a page
 */
public record Pagination(int offset, int total, int pageSize) {

    public Pagination {
        Preconditions.checkState(offset >= 0);
        Preconditions.checkState(total >= 0);
        Preconditions.checkState(0 == total || offset < total);
        Preconditions.checkState(pageSize >= 1);
    }

    public boolean isEmpty() {
        return 0 == total;
    }

    public Page currentPage() {
        return createPageForPageNumber(currentPageNumber());
    }

    public boolean hasPreviousPage() {
        return offset >= pageSize;
    }

    @Nullable
    public Page previousPage() {
        if (offset >= pageSize) {
            int pageNumber = (offset / pageSize) - 1;
            return new Page(pageNumber, pageNumber * pageSize, false);
        }
        return null;
    }

    public boolean hasNextPage() {
        return offset < total - pageSize;
    }

    @Nullable
    public Page nextPage() {
        if (offset < total - pageSize) {
            int pageNumber = (offset / pageSize) + 1;
            return new Page(pageNumber, pageNumber * pageSize, false);
        }
        return null;
    }

    public int pageCount() {
        return (total / pageSize) + (0 != total % pageSize ? 1 : 0);
    }

    /**
     * <p>This method will return an integer array containing item offsets that can be taken to be handy
     * pages that the user might like to jump to.  This can then be used to present a list of pages within
     * a list of data.  The returned pages try to present a set of smart pages to jump to and may not be
     * strictly linear.  If there is only one page then you may be returned an empty array.</p>
     */

    public List<Page> generateSuggestedPages(final int suggestions) {
        Preconditions.checkState(suggestions > 3,"the count of pages must be more than 0");

        if (total == 0) {
            return List.of();
        }

        if (total == 1) {
            return List.of(currentPage());
        }

        final int currentPageNumber = currentPageNumber();
        final int pageCount = pageCount();

        if (pageCount <= suggestions) {
            return IntStream.range(0, pageCount)
                    .mapToObj(this::createPageForPageNumber)
                    .toList();
        }

        final int idealSpreadLeft = (suggestions / 2);
        final int idealSpreadRight = suggestions - (suggestions / 2 + 1);
        final int idealSpreadRightOverflow = Math.max(0, (idealSpreadRight + currentPageNumber) - (pageCount - 1));
        final int startPageInclusive = Math.max(0, currentPageNumber - (idealSpreadLeft + idealSpreadRightOverflow));
        final int endPageExclusive = Math.min(pageCount, startPageInclusive + suggestions); // exclusive

        return IntStream.range(startPageInclusive, endPageExclusive)
                .mapToObj(i -> {
                    Preconditions.checkState(i >= 0 && i < pageCount, "page out of range");

                    if (i == startPageInclusive && i != 0) {
                        return createPageForPageNumber(0);
                    }
                    if (i == endPageExclusive - 1 && i != pageCount - 1) {
                        return createPageForPageNumber(pageCount - 1);
                    }
                    return createPageForPageNumber(i);
                })
                .toList();
    }

    private int currentPageNumber() {
        return offset / pageSize;
    }

    private Page createPageForPageNumber(int pageNumber) {
        return new Page(pageNumber, pageNumber * pageSize, currentPageNumber() == pageNumber);
    }

    @Override
    public String toString() {
        return "pagination; " + offset + " in " + total;
    }

}
