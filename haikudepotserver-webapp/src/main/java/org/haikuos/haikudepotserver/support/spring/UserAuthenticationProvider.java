/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.spring;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.Hashing;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectIdQuery;
import org.haikuos.haikudepotserver.model.User;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class UserAuthenticationProvider implements AuthenticationProvider {

    private ServerRuntime serverRuntime;

    Cache<String,ObjectId> userNicknameToObjectIdCache = CacheBuilder
            .newBuilder()
            .maximumSize(256)
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .build();

    public ServerRuntime getServerRuntime() {
        return serverRuntime;
    }

    public void setServerRuntime(ServerRuntime serverRuntime) {
        this.serverRuntime = serverRuntime;
    }

    /**
     * <p>This method will map the nickname to an {@link ObjectId} and in this way, it is possible to
     * pull the {@link User} from the cache in Cayenne rather than re-fetching it from the database.  This
     * will make a series of API interactions with the system less computationally expensive.</p>
     */

    private Optional<User> getUserByNickname(final ObjectContext context, final String nickname) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(nickname));

        ObjectId objectId = userNicknameToObjectIdCache.getIfPresent(nickname);

        if(null!=objectId) {
            List<User> users = context.performQuery(new ObjectIdQuery(
                    objectId,
                    false,
                    ObjectIdQuery.CACHE_NOREFRESH));

            if(1!=users.size()) {
                throw new IllegalStateException("zero or more than one found for an object-id");
            }

            return Optional.of(users.get(0));
        }
        else {
            Optional<User> userOptional = User.getByNickname(context, nickname);

            if(userOptional.isPresent()) {
                userNicknameToObjectIdCache.put(nickname, userOptional.get().getObjectId());
            }

            return userOptional;
        }
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        Preconditions.checkNotNull(authentication);
        Preconditions.checkNotNull(serverRuntime);

        String nickname = authentication.getName();
        String passwordClear = (String) authentication.getCredentials();

        if(Strings.isNullOrEmpty(nickname) || Strings.isNullOrEmpty(passwordClear)) {
            throw new BadCredentialsException("no nickname or password supplied; unable to authenticate the user");
        }

        ObjectContext objectContext = serverRuntime.getContext();

        Optional<User> userOptional = getUserByNickname(objectContext, nickname);

        if(!userOptional.isPresent()) {
            throw new UsernameNotFoundException("unable to find the user; "+nickname);
        }

        User user = userOptional.get();
        String hash = Hashing.sha256().hashUnencodedChars(user.getPasswordSalt() + passwordClear).toString();

        if(hash.equals(user.getPasswordHash())) {
            return new UserAuthentication(user);
        }

        throw new BadCredentialsException("bad password supplied");
    }

    @Override
    public boolean supports(Class<?> aClass) {
        return true;
    }
}
