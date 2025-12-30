/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haiku.haikudepotserver.dataobjects.auto._JobData;
import org.haiku.haikudepotserver.dataobjects.support.MutableCreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;

import java.util.Optional;
import java.util.regex.Pattern;

public class JobData extends _JobData implements MutableCreateAndModifyTimestamped {

    private static final long serialVersionUID = 1L;

    private final static Pattern PATTERN_CODE = Pattern.compile("^[a-z0-9-]{36}$");
    private final static Pattern PATTERN_USE_CODE = Pattern.compile("^[a-z0-9-]+$");

    public static Optional<JobData> tryGetByCode(ObjectContext context, String code) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));

        return Optional.ofNullable(ObjectSelect.query(JobData.class).where(CODE.eq(code))
                .selectOne(context));
    }

    public static JobData getByCode(ObjectContext context, String code) {
        return tryGetByCode(context, code)
                .orElseThrow(() -> new ObjectNotFoundException(JobData.class.getSimpleName(), code));
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if (null != getCode()) {
            if (!PATTERN_CODE.matcher(getCode()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, CODE.getName(), "malformed"));
            }
        }

        if (null != getUseCode()) {
            if (!PATTERN_USE_CODE.matcher(getUseCode()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, USE_CODE.getName(), "malformed"));
            }
        }

    }
}
