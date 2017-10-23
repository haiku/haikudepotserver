/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haiku.haikudepotserver.dataobjects.auto._UserPasswordResetToken;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class UserPasswordResetToken extends _UserPasswordResetToken {

    /**
     * <p>This pattern should be safe for use in URLs.</p>
     */

    private final static Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9-]{36,36}+$");

    public static List<UserPasswordResetToken> findByUser(ObjectContext context, User user) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != user, "the user must be supplied");
        return ObjectSelect.query(UserPasswordResetToken.class).where(USER.eq(user)).select(context);
    }

    public static Optional<UserPasswordResetToken> getByCode(ObjectContext context, String code) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "the code must be provided");
        return Optional.ofNullable(ObjectSelect
                .query(UserPasswordResetToken.class)
                .where(CODE.eq(code))
                .selectOne(context));
    }

    /**
     * <p>This method will return true if the system has any {@link UserPasswordResetToken} objects
     * stored.  There is no need to perform maintenance tasks for this if not.</p>
     */

    public static boolean hasAny(ObjectContext context) {
        Preconditions.checkArgument(null != context, "a context must be provided");
        return ObjectSelect.query(UserPasswordResetToken.class).count().selectFirst(context) > 0;
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null != getCode()) {
            if(!CODE_PATTERN.matcher(getCode()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, CODE.getName(), "malformed"));
            }
        }
    }

}
