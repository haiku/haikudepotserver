/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.pkg.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * <p>Various elements of the {@link org.haiku.haikudepotserver.dataobjects.Pkg} can be
 * localized and this enum identifies which element.</p>
 */

public enum PkgLocalizationContentType {
    TITLE,
    SUMMARY,
    DESCRIPTION;

    public static Optional<PkgLocalizationContentType> tryFromCode(String code) {
        return Arrays.stream(values()).filter(v -> v.getCode().equals(code)).findFirst();
    }

    public String getCode() {
        return name().toLowerCase(Locale.ROOT);
    }
}
