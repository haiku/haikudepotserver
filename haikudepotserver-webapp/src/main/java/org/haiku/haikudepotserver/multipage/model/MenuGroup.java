/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.model;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

public record MenuGroup (
    @Nullable String titleKey,
    List<MenuItem> items
) {

    public MenuGroup {
        Preconditions.checkArgument(null == titleKey || titleKey.startsWith("mp."));
        Preconditions.checkArgument(null != items);
    }

    public boolean isEmpty() {
        return CollectionUtils.isEmpty(items);
    }

}
