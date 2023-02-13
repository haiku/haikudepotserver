/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.query.ObjectIdQuery;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.haiku.haikudepotserver.dataobjects.auto._User;
import org.haiku.haikudepotserver.dataobjects.support.MutableCreateAndModifyTimestamped;
import org.haiku.haikudepotserver.security.model.AuthorizationPkgRule;
import org.haiku.haikudepotserver.support.SingleCollector;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class User extends _User implements MutableCreateAndModifyTimestamped {

    private final static Pattern NICKNAME_PATTERN = Pattern.compile("^[a-z0-9]{4,16}$");
    private final static Pattern PASSWORDHASH_PATTERN = Pattern.compile("^[a-f0-9]{64}$");
    private final static Pattern PASSWORDSALT_PATTERN = Pattern.compile("^[a-f0-9]{10,32}$");

    public static List<User> findByEmail(ObjectContext context, String email) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(email), "the email must be supplied");
        return ObjectSelect.query(User.class).where(EMAIL.eq(email)).select(context);
    }

    public static User getByObjectId(ObjectContext context, ObjectId objectId) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != objectId, "the objectId must be supplied");
        Preconditions.checkArgument(objectId.getEntityName().equals(User.class.getSimpleName()), "the objectId must be targetting the User entity");

        ObjectIdQuery objectIdQuery = new ObjectIdQuery(
                objectId,
                false, // fetching data rows
                ObjectIdQuery.CACHE_NOREFRESH);

        List result = context.performQuery(objectIdQuery);

        switch(result.size()) {
            case 0: throw new IllegalStateException("unable to find the user from the objectid; " + objectId.toString());
            case 1: return (User) result.get(0);
            default: throw new IllegalStateException("more than one user returned from an objectid lookup");
        }
    }

    public static User getByNickname(ObjectContext context, String nickname) {
        return tryGetByNickname(context, nickname)
                .orElseThrow(() -> new IllegalStateException("unable to get the user for nickname [" + nickname + "]"));
    }

    public static Optional<User> tryGetByNickname(ObjectContext context, String nickname) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(nickname));

        return Optional.ofNullable(ObjectSelect.query(User.class).where(NICKNAME.eq(nickname))
                .sharedCache()
                .cacheGroup(HaikuDepot.CacheGroup.USER.name())
                .selectOne(context));
    }

    // configured as a listener method in the model.

    public void onPostAdd() {

        if(null==getIsRoot()) {
            setIsRoot(Boolean.FALSE);
        }

        if(null==getCanManageUsers()) {
            setCanManageUsers(Boolean.FALSE);
        }

        if(null==getActive()) {
            setActive(Boolean.TRUE);
        }

        // create and modify timestamp handled by listener.
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null==getIsRoot()) {
            setIsRoot(Boolean.FALSE);
        }

        if(null != getNickname()) {
            if(!NICKNAME_PATTERN.matcher(getNickname()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, NICKNAME.getName(), "malformed"));
            }
        }

        if(null != getPasswordHash()) {
            if(!PASSWORDHASH_PATTERN.matcher(getPasswordHash()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, PASSWORD_HASH.getName(), "malformed"));
            }
        }

        if(null != getPasswordSalt()) {
            if(!PASSWORDSALT_PATTERN.matcher(getPasswordSalt()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, PASSWORD_SALT.getName(), "malformed"));
            }
        }

        if(null != getEmail()) {
            try {
                InternetAddress internetAddress = new InternetAddress(getEmail());
                internetAddress.validate();
            }
            catch(AddressException ae) {
                validationResult.addFailure(new BeanValidationFailure(this, EMAIL.getName(), "malformed"));
            }
        }

    }

    public Optional<UserUsageConditionsAgreement> tryGetUserUsageConditionsAgreement() {
        return getUserUsageConditionsAgreements()
                .stream()
                .filter(UserUsageConditionsAgreement::getActive)
                .collect(SingleCollector.optional());
    }

    /**
     * <p>This method will return all of the rules pertaining to the supplied package; including those
     * rules that might apply to any package.</p>
     */

    public List<? extends AuthorizationPkgRule> getAuthorizationPkgRules(final Pkg pkg) {
        Preconditions.checkNotNull(pkg);

        return getPermissionUserPkgs()
                .stream()
                .filter(pup -> null == pup.getPkg() || pup.getPkg().equals(pkg))
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("nickname", getNickname())
                .build();
    }

}
