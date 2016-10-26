/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haiku.haikudepotserver.dataobjects.auto._UserPasswordResetToken;
import org.haiku.haikudepotserver.support.SingleCollector;

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
        return context.performQuery(new SelectQuery(
                UserPasswordResetToken.class,
                ExpressionFactory.matchExp(UserPasswordResetToken.USER_PROPERTY, user)));
    }

    public static Optional<UserPasswordResetToken> getByCode(ObjectContext context, String code) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "the code must be provided");
        return ((List<UserPasswordResetToken>) context.performQuery(new SelectQuery(
                        UserPasswordResetToken.class,
                        ExpressionFactory.matchExp(UserPasswordResetToken.CODE_PROPERTY, code))))
                .stream()
                .collect(SingleCollector.optional());
    }

    /**
     * <p>This method will return true if the system has any {@link UserPasswordResetToken} objects
     * stored.  There is no need to perform maintenance tasks for this if not.</p>
     */

    public static boolean hasAny(ObjectContext context) {
        Preconditions.checkArgument(null!=context, "a context must be provided");
        SelectQuery query = new SelectQuery(UserPasswordResetToken.class);
        query.setFetchLimit(1);
        return 0 != context.performQuery(query).size();
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
