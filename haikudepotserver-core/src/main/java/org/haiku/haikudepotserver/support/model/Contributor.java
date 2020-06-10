package org.haiku.haikudepotserver.support.model;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * <p>This record captures a person who has contributed to the project
 * in some way.</p>
 */

public class Contributor {

    public enum Type {
        ENGINEERING,
        LOCALIZATION
    }

    private final String name;
    private final Type type;
    private final String naturalLanguageCode;

    public Contributor(Type type, String name) {
        this(type, name, null);
    }

    public Contributor(Type type, String name, String naturalLanguageCode) {
        Preconditions.checkArgument(null != type);
        Preconditions.checkArgument(StringUtils.isNotBlank(name));
        this.name = name;
        this.type = type;
        this.naturalLanguageCode = naturalLanguageCode;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public String getNaturalLanguageCode() {
        return naturalLanguageCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Contributor that = (Contributor) o;

        return new EqualsBuilder()
                .append(name, that.name)
                .append(type, that.type)
                .append(naturalLanguageCode, that.naturalLanguageCode)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(name)
                .append(type)
                .append(naturalLanguageCode)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("type", getType())
                .append("name", getName())
                .append("nlcode", getNaturalLanguageCode())
                .build();
    }

}
