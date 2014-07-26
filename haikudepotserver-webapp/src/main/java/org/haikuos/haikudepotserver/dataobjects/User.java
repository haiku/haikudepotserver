/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.ObjectIdQuery;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.dataobjects.auto._User;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haikuos.haikudepotserver.security.model.AuthorizationPkgRule;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class User extends _User implements CreateAndModifyTimestamped {

    public final static Pattern NICKNAME_PATTERN = Pattern.compile("^[a-z0-9]{4,16}$");
    public final static Pattern PASSWORDHASH_PATTERN = Pattern.compile("^[a-f0-9]{64}$");
    public final static Pattern PASSWORDSALT_PATTERN = Pattern.compile("^[a-f0-9]{64}$");

    public static List<User> findByEmail(ObjectContext context, String email) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(email));
        return context.performQuery(new SelectQuery(
                User.class,
                ExpressionFactory.matchExp(User.EMAIL_PROPERTY, email)
        ));
    }

    public static User getByObjectId(ObjectContext context, ObjectId objectId) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(objectId);
        Preconditions.checkState(objectId.getEntityName().equals(User.class.getSimpleName()));

        ObjectIdQuery objectIdQuery = new ObjectIdQuery(
                objectId,
                false, // fetching data rows
                ObjectIdQuery.CACHE_NOREFRESH);

        List result = context.performQuery(objectIdQuery);

        switch(result.size()) {
            case 0:
                throw new IllegalStateException("unable to find the user from the objectid; " + objectId.toString());

            case 1:
                return (User) result.get(0);

            default:
                throw new IllegalStateException("more than one user returned from an objectid lookup");
        }
    }

    public static Optional<User> getByNickname(ObjectContext context, String nickname) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(nickname));
        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<User>) context.performQuery(new SelectQuery(
                        User.class,
                        ExpressionFactory.matchExp(User.NICKNAME_PROPERTY, nickname))),
                null));
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

        if(null==getPasswordSalt()) {
            setPasswordSalt(UUID.randomUUID().toString());
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
                validationResult.addFailure(new BeanValidationFailure(this,NICKNAME_PROPERTY,"malformed"));
            }
        }

        if(null != getPasswordHash()) {
            if(!PASSWORDHASH_PATTERN.matcher(getPasswordHash()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this,PASSWORD_HASH_PROPERTY,"malformed"));
            }
        }

        if(null != getPasswordSalt()) {
            if(!PASSWORDSALT_PATTERN.matcher(getPasswordSalt()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this,PASSWORD_HASH_PROPERTY,"malformed"));
            }
        }

        if(null != getEmail()) {
            try {
                InternetAddress internetAddress = new InternetAddress(getEmail());
                internetAddress.validate();
            }
            catch(AddressException ae) {
                validationResult.addFailure(new BeanValidationFailure(this,EMAIL_PROPERTY,"malformed"));
            }
        }

    }

    /**
     * <p>This method will return all of the rules pertaining to the supplied package; including those
     * rules that might apply to any package.</p>
     */

    public List<? extends AuthorizationPkgRule> getAuthorizationPkgRules(final Pkg pkg) {
        Preconditions.checkNotNull(pkg);

        return ImmutableList.copyOf(Iterables.filter(
                getPermissionUserPkgs(),
                new Predicate<PermissionUserPkg>() {
                    @Override
                    public boolean apply(PermissionUserPkg input) {
                        return null==input.getPkg() || input.getPkg().equals(pkg);
                    }
                }
        ));
    }

    /**
     * <p>This method will configure a random salt value.</p>
     */

    public void setPasswordSalt() {
        setPasswordSalt(Hashing.sha256().hashUnencodedChars(UUID.randomUUID().toString()).toString());
    }

    public Boolean getDerivedCanManageUsers() {
        return getCanManageUsers() || getIsRoot();
    }

    @Override
    public String toString() {
        return "user;"+getNickname();
    }

}
