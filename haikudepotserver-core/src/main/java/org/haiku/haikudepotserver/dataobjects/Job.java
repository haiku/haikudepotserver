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
import org.haiku.haikudepotserver.dataobjects.auto._Job;
import org.haiku.haikudepotserver.dataobjects.support.MutableCreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;

import java.io.Serial;
import java.util.Optional;
import java.util.regex.Pattern;

public class Job extends _Job implements MutableCreateAndModifyTimestamped {

    @Serial
    private static final long serialVersionUID = 1L;

    private final static Pattern PATTERN_CODE = Pattern.compile("^[a-z0-9-]{36}");

    public static Optional<Job> tryGetByCode(ObjectContext context, String code) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));

        return Optional.ofNullable(ObjectSelect.query(Job.class).where(CODE.eq(code))
                .selectOne(context));
    }

    public static Job getByCode(ObjectContext context, String code) {
        return tryGetByCode(context, code)
                .orElseThrow(() -> new ObjectNotFoundException(Job.class.getSimpleName(), code));
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if (null != getCode()) {
            if (!PATTERN_CODE.matcher(getCode()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, CODE.getName(), "malformed"));
            }
        }

        if (null != getOwnerUserNickname()) {
            if (!User.NICKNAME_PATTERN.matcher(getOwnerUserNickname()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, OWNER_USER_NICKNAME.getName(), "malformed"));
            }
        }

        if (null != getFinishTimestamp() && null == getStartTimestamp()) {
            validationResult.addFailure(new BeanValidationFailure(this, FINISH_TIMESTAMP.getName(), "start_required_when_finished"));
        }

        if (null != getStartTimestamp() && null == getQueueTimestamp()) {
            validationResult.addFailure(new BeanValidationFailure(this, START_TIMESTAMP.getName(), "queued_required_when_started"));
        }

        if (null != getFinishTimestamp() && (null == getProgressPercent() || 100 != getProgressPercent())) {
            validationResult.addFailure(new BeanValidationFailure(this, PROGRESS_PERCENT.getName(), "progress_100_required_when_finished"));
        }

        if (null != getProgressPercent() && (null == getFinishTimestamp() && null == getStartTimestamp())) {
            validationResult.addFailure(new BeanValidationFailure(this, PROGRESS_PERCENT.getName(), "start_or_finish_required_when_progress"));
        }

        // This field is a JSON object encoded as a string so it should start with '{' and end with '}'
        if (!getSpecification().startsWith("{") || !getSpecification().endsWith("}")) {
            validationResult.addFailure(new BeanValidationFailure(this, SPECIFICATION.getName(), "malformed"));
        }
    }

}
