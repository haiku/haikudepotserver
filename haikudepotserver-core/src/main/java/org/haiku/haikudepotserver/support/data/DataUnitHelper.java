/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.data;

public class DataUnitHelper {

    /**
     * <p>People don't typically reason about time in millions of milliseconds, they
     * might think of hours. Same with data; 10000000 bytes is 9.5 megabytes. This
     * function will take a raw number of bytes and convert that into a convenient
     * {@link DataQuantity} incorporating both a unit and a measure of data.</p>
     */

    public static DataQuantity scaleBytesToSensibleUnit(long bytes) {
        for (int i = DataUnit.values().length - 1; i >= 0; i--) {
            DataUnit unit = DataUnit.values()[i];
            long bytesPerUnit = Math.powExact(1024, i);

            if (bytes >= bytesPerUnit || 0 == i) {
                return switch (unit) {
                    case BYTE -> new DataQuantity(bytes, DataUnit.BYTE);
                    default -> new DataQuantity((float) bytes / (float) bytesPerUnit, unit);
                };
            }
        }
        throw new IllegalStateException("unable to create data quantity");
    }

}
