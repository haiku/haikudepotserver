/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.model;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

public record Defaults (
    String defaultArchitectureCode,
    String defaultRepositoryCode,
    String defaultRepositorySourceCode
) {

    public Defaults {
        Preconditions.checkArgument(StringUtils.isNotBlank(defaultArchitectureCode), "default architecture code must be provided");
        Preconditions.checkArgument(StringUtils.isNotBlank(defaultRepositoryCode), "default repository code must be provided");
        Preconditions.checkArgument(StringUtils.isNotBlank(defaultRepositorySourceCode), "default repository source code must be provided");
    }

}
