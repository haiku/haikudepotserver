/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.query.ObjectIdQuery;
import org.haikuos.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haikuos.haikudepotserver.model.User;
import org.haikuos.haikudepotserver.support.spring.UserAuthentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * <p>This abstract superclass of the API implementations allows access to the presently authenticated user.  The
 * principal is the {@link ObjectId} if the authenticated user and in this way, by providing an {@link ObjectContext},
 * the User object can be obtained fairly (computationally) inexpensively.</p>
 */

public abstract class AbstractApiImpl {

    /**
     * <P>This method will get the currently authenticated user.  If there is no authenticated user then this method
     * will throw an instance of {@link AuthorizationFailureException}.</P>
     */

    protected User obtainAuthenticatedUser(ObjectContext objectContext) throws AuthorizationFailureException {
        Optional<User> userOptional = tryObtainAuthenticatedUser(objectContext);

        if(!userOptional.isPresent()) {
            throw new AuthorizationFailureException();
        }

        return userOptional.get();
    }

    /**
     * <P>This method will (optionally) return a user that represents the currently authenticated user.  It will not
     * return null.</P>
     */

    protected Optional<User> tryObtainAuthenticatedUser(ObjectContext objectContext) {
        Preconditions.checkNotNull(objectContext);

        UserAuthentication userAuthentication = (UserAuthentication) SecurityContextHolder.getContext().getAuthentication();

        if(null!=userAuthentication) {

            ObjectId objectId = (ObjectId) userAuthentication.getPrincipal();

            if(!objectId.getEntityName().equals(User.class.getSimpleName())) {
                throw new IllegalStateException("the object-id for the user authentication must be a user");
            }

            ObjectIdQuery objectIdQuery = new ObjectIdQuery(
                    objectId,
                    false, // fetching data rows
                    ObjectIdQuery.CACHE_NOREFRESH);

            List result = objectContext.performQuery(objectIdQuery);

            switch(result.size()) {
                case 0:
                    break;

                case 1:
                    return Optional.of((User) result.get(0));

                default:
                    throw new IllegalStateException("more than one user returned from an objectid lookup");
            }
        }

        return Optional.absent();
    }

}
