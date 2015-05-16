/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.dataobjects.auto._UserPasswordResetToken;
import org.haikuos.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class UserPasswordResetToken extends _UserPasswordResetToken {

    /**
     * <p>This pattern should be safe for use in URLs.</p>
     */

    private final static Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9-]{36,36}+$");

    public static List<UserPasswordResetToken> findByUser(ObjectContext context, User user) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(user);
        return context.performQuery(new SelectQuery(
                UserPasswordResetToken.class,
                ExpressionFactory.matchExp(UserPasswordResetToken.USER_PROPERTY, user)));
    }

    public static Optional<UserPasswordResetToken> getByCode(ObjectContext context, String code) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));
        return ((List<UserPasswordResetToken>) context.performQuery(new SelectQuery(
                        UserPasswordResetToken.class,
                        ExpressionFactory.matchExp(UserPasswordResetToken.CODE_PROPERTY, code))))
                .stream()
                .collect(SingleCollector.optional());
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null != getCode()) {
            if(!CODE_PATTERN.matcher(getCode()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this,CODE_PROPERTY,"malformed"));
            }
        }
    }

}
