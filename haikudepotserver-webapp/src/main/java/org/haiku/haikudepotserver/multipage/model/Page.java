/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.model;

import com.google.common.base.Preconditions;

/**
 * <p>In a pagination, you have a series of pages that you can navigate to. Page 1, page 2, page 3 etc... This record
 * models one of those pages.</p>
 * @param pageNumber Page 1, page 2, page 3.
 * @param offset is the offset into the data. For example, you may have 17 items with a page size of 10 would give you
 *               two pages; the first with an {@code offset} of 0 and the second with an {@code offset} of 10.
 * @param isCurrent is {@code true} if this page is the current page.
 */

public record Page(int pageNumber, int offset, boolean isCurrent) {

    public Page {
        Preconditions.checkArgument(pageNumber >= 0, "pageNumber must be >= 0");
        Preconditions.checkArgument(offset >= 0, "offset must be >= 0");
    }

}
