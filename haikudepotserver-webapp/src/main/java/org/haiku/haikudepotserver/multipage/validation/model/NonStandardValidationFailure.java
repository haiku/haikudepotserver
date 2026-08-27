/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.validation.model;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Objects;

public class NonStandardValidationFailure implements ValidationFailure {

    private final String key;

    private final Object[] params;

    public NonStandardValidationFailure(String key) {
        this(key, null);
    }

    public NonStandardValidationFailure(String key, Object[] params) {
        Preconditions.checkArgument(StringUtils.isNotBlank(key));
        Preconditions.checkArgument(key.startsWith("mp."));
        this.key = key;
        this.params = Objects.requireNonNullElseGet(params, () -> new Object[] {});
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Object[] getParams() {
        return params;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("key", key)
                .append("params", params)
                .toString();
    }
}
