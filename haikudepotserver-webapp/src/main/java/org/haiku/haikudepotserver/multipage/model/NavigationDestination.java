/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.model;

import com.google.common.base.CaseFormat;

public enum NavigationDestination {

    HOME,

    ABOUT,
    ABOUT_HAIKU,

    USER_USAGE_CONDITIONS,

    PKG_CHANGELOG,
    PKG_VIEW,
    PKG_LIST;

    private final String template;

    NavigationDestination() {
        this.template = "multipage/%s"
                .formatted(CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, name()));
    }

    public String template() {
        return template;
    }

}
