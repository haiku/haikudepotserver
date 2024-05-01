/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.haiku.haikudepotserver.dataobjects.auto._RepositorySourceMirror;
import org.haiku.haikudepotserver.dataobjects.support.MutableCreateAndModifyTimestamped;

import java.io.Serial;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

public class RepositorySourceMirror
        extends _RepositorySourceMirror
        implements MutableCreateAndModifyTimestamped, Comparable<RepositorySourceMirror> {

    @Serial
    private static final long serialVersionUID = 1L;

    public static RepositorySourceMirror getByCode(
            ObjectContext context, String code) {
        return tryGetByCode(context, code)
                .orElseThrow(() -> new ObjectNotFoundException(
                        RepositorySourceMirror.class.getSimpleName(), code));
    }

    public static Optional<RepositorySourceMirror> tryGetByCode(
            ObjectContext context, String code) {
        Preconditions.checkArgument(null != context);
        Preconditions.checkArgument(null != code);

        return Optional.ofNullable(ObjectSelect.query(RepositorySourceMirror.class)
                .where(CODE.eq(code))
                .selectFirst(context));
    }

    public static List<RepositorySourceMirror> findByRepositorySource(
            ObjectContext context,
            RepositorySource repositorySource,
            boolean includeInactive) {
        Preconditions.checkArgument(null != context);
        Preconditions.checkArgument(null != repositorySource);

        ObjectSelect<RepositorySourceMirror> select = ObjectSelect.query(RepositorySourceMirror.class)
                .where(REPOSITORY_SOURCE.eq(repositorySource));

        if (!includeInactive) {
            select.and(ACTIVE.eq(Boolean.TRUE));
        }

        select.orderBy(ACTIVE.asc(), COUNTRY.dot(Country.CODE).asc(), CREATE_TIMESTAMP.desc());

        return select.select(context);
    }

    @Override
    public void validateForInsert(ValidationResult validationResult) {
        if(null==getActive()) {
            setActive(Boolean.TRUE);
        }

        super.validateForInsert(validationResult);
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if (getIsPrimary() && !getActive()) {
            validationResult.addFailure(new BeanValidationFailure(
                    this, IS_PRIMARY.getName(), "mustbeactive"));
        }

        if(null != getBaseUrl()) {
            try {
                new URL(getBaseUrl());
            }
            catch(MalformedURLException mue) {
                validationResult.addFailure(new BeanValidationFailure(
                        this, BASE_URL.getName(), "malformed"));
            }
        }

        if(null != getBaseUrl()) {
            if(getBaseUrl().endsWith("/")) {
                validationResult.addFailure(new BeanValidationFailure(
                        this, BASE_URL.getName(), "trailingslash"));
            }
        }

    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("country", getCountry().getCode())
                .build();
    }

    @Override
    public int compareTo(RepositorySourceMirror other) {
        return new CompareToBuilder()
                .append(other.getIsPrimary(), getIsPrimary())
                .append(other.getCountry().getCode(), getCountry().getCode())
                .append(other.getCode(), getCode())
                .build();
    }
}
