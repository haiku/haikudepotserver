/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.validation.model;

import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Objects;

public class StandardValidationFailure implements ValidationFailure {

    public enum Type {
        /**
         * <p>The value of a boolean must be {@code true}; an example is where the user must
         * select a checkbox to be able to progress.</p>
         */
        REQUIRED_TRUE,
    }

    private final Type type;

    private final Object[] params;

    public StandardValidationFailure(Type type) {
        this(type, null);
    }

    public StandardValidationFailure(Type type, Object[] params) {
        this.type = Preconditions.checkNotNull(type);
        this.params = Objects.requireNonNullElseGet(params, () -> new Object[] {});
    }

    @Override
    public String getKey() {
        return "mp.gen.validation.%s.title".formatted(
                CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_UNDERSCORE, type.name()));
    }

    @Override
    public Object[] getParams() {
        return params;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("type", type)
                .append("params", params)
                .toString();
    }
}
