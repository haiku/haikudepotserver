/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.model;

import com.google.common.base.CaseFormat;
import org.springframework.web.util.UriComponentsBuilder;

public record MenuItem(
        NavigationDestination navigationDestination,
        UriComponentsBuilder uriComponentsBuilder
) {

    public String titleKey() {
        return "mp.menu.item.%s.title".formatted(
                CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_UNDERSCORE, navigationDestination.name())
        );
    }

}
