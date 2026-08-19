/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.data;

/**
 * <p>This enum describes the unit of a quantity of data.</p>
 */

public enum DataUnit {
    BYTE("bytes"),
    KILOBYTE("KiB"),
    MEGABYTE("MiB"),
    GIGABYTE("GiB");

    private final String suffix;

    DataUnit(String suffix) {
        this.suffix = suffix;
    }

    public String getSuffix() {
        return suffix;
    }
}
