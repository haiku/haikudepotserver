/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.security;

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
import org.haikuos.haikudepotserver.dataobjects.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>This service is able to provide the ability to authenticate a user given their nickname and their clear-text
 * password.  It will maintain a cache of nickname to {@link ObjectId}s so that it is able to lookup users very quickly
 * if they are known to this instance.  This may be useful in a small-scale deployment.  This class is accessed by
 * the {@link org.haikuos.haikudepotserver.security.AuthenticationFilter}.</p>
 */

@Service
public class AuthenticationService {

    protected static Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

    @Resource
    ServerRuntime serverRuntime;

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
     * pull the {@link org.haikuos.haikudepotserver.dataobjects.User} from the cache in Cayenne rather than re-fetching it from the database.  This
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

    public Optional<ObjectId> authenticate(String username, String passwordClear) {

        Optional<ObjectId> result = Optional.absent();

        if(!Strings.isNullOrEmpty(username) && !Strings.isNullOrEmpty(passwordClear)) {

            ObjectContext objectContext = serverRuntime.getContext();

            Optional<User> userOptional = getUserByNickname(objectContext, username);

            if(userOptional.isPresent()) {
                User user = userOptional.get();
                String hash = Hashing.sha256().hashUnencodedChars(user.getPasswordSalt() + passwordClear).toString();

                if(hash.equals(user.getPasswordHash())) {
                    result = Optional.fromNullable(userOptional.get().getObjectId());
                }
                else {
                    logger.info("the authentication for the user; {} failed", username);
                }
            }
            else {
                logger.info("unable to find the user; {}", username);
            }
        }
        else {
            logger.info("attempt to authenticate with no username or no password");
        }

        return result;
    }

}
