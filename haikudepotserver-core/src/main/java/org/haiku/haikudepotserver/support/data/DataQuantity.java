/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.data;

import com.google.common.base.Preconditions;

/**
 * <p>This record couples a quantity of data with the unit that the quantity is
 * measured in such as &quot;10 megabytes&quot;</p>
 */

public record DataQuantity(Number quantity, DataUnit unit) {

    public DataQuantity {
        Preconditions.checkArgument(null != unit, "unit must be provided");
        Preconditions.checkArgument(null != quantity, "quantity must be provided");
    }

}
