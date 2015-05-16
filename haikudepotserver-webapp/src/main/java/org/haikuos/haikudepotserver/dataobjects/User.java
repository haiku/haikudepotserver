/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.ObjectIdQuery;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.dataobjects.auto._User;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haikuos.haikudepotserver.security.model.AuthorizationPkgRule;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class User extends _User implements CreateAndModifyTimestamped {

    public final static String NICKNAME_ROOT = "root";

    public final static Pattern NICKNAME_PATTERN = Pattern.compile("^[a-z0-9]{4,16}$");
    public final static Pattern PASSWORDHASH_PATTERN = Pattern.compile("^[a-f0-9]{64}$");
    public final static Pattern PASSWORDSALT_PATTERN = Pattern.compile("^[a-f0-9]{10,32}$");

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
            case 0: throw new IllegalStateException("unable to find the user from the objectid; " + objectId.toString());
            case 1: return (User) result.get(0);
            default: throw new IllegalStateException("more than one user returned from an objectid lookup");
        }
    }

    public static Optional<User> getByNickname(ObjectContext context, String nickname) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(nickname));

        SelectQuery query = new SelectQuery(User.class, ExpressionFactory.matchExp(User.NICKNAME_PROPERTY, nickname));
        query.setCacheGroups(HaikuDepot.CacheGroup.USER.name());
        query.setCacheStrategy(QueryCacheStrategy.SHARED_CACHE);

        List<User> users = context.performQuery(query);

        switch(users.size()) {
            case 0: return Optional.empty();
            case 1: return Optional.of(users.get(0));
            default: throw new IllegalStateException("found more than one user for nickname; " + nickname);
        }
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

        return getPermissionUserPkgs()
                .stream()
                .filter(pup -> null == pup.getPkg() || pup.getPkg().equals(pkg))
                .collect(Collectors.toList());
    }

    /**
     * <p>The LDAP entry for a person can have a 'userPassword' attribute.  This has the format
     * {SSHA-256}&lt;base64data&gt;.  The base 64 data, decoded to bytes contains 20 bytes of
     * hash and the rest is salt.</p>
     *
     * <p>This method will convert the information stored in this user over into a format suitable
     * for storage in this format for LDAP.</p>
     *
     * <p>SSHA stands for salted SHA hash.</p>
     *
     * <p>RFC-2307</p>
     */

    public String toLdapUserPasswordAttributeValue() {
        StringBuilder builder = new StringBuilder();
        builder.append("{SSHA-256}");

        byte[] hash = BaseEncoding.base16().decode(getPasswordHash().toUpperCase());

        if(32 != hash.length) {
            throw new IllegalStateException("the password hash should be 20 bytes long");
        }

        byte[] salt = BaseEncoding.base16().decode(getPasswordSalt().toUpperCase());

        byte[] hashAndSalt = new byte[hash.length + salt.length];
        System.arraycopy(hash, 0, hashAndSalt, 0, hash.length);
        System.arraycopy(salt, 0, hashAndSalt, hash.length, salt.length);

        return "{SSHA-256}" + BaseEncoding.base64().encode(hashAndSalt);
    }

    /**
     * <p>This method will configure a random salt value.</p>
     */

    public void setPasswordSalt() {
        String randomHash = Hashing.sha256().hashUnencodedChars(UUID.randomUUID().toString()).toString();
        setPasswordSalt(randomHash.substring(0,16)); // LDAP server doesn't seem to like very long salts
    }

    public Boolean getDerivedCanManageUsers() {
        return getCanManageUsers() || getIsRoot();
    }

    @Override
    public String toString() {
        return "user;"+getNickname();
    }

}
