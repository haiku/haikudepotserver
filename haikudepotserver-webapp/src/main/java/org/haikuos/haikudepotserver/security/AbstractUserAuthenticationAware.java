/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.security;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.query.ObjectIdQuery;
import org.haikuos.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haikuos.haikudepotserver.dataobjects.User;

import java.util.List;

/**
 * <p>This class is the superclass for objects that are involved in the request response cycle that would like to
 * obtain the currently authenticated user.  The currently authenticated user would have been authenticated using
 * a servlet filter and then would be available from a thread-local.</p>
 */

public abstract class AbstractUserAuthenticationAware {

    /**
     * <P>This method will get the currently authenticated user.  If there is no authenticated user then this method
     * will throw an instance of {@link org.haikuos.haikudepotserver.api1.support.AuthorizationFailureException}.</P>
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

        Optional<ObjectId> authenticatedUserObjectId = AuthenticationHelper.getAuthenticatedUserObjectId();

        if(authenticatedUserObjectId.isPresent()) {

            ObjectId objectId = authenticatedUserObjectId.get();

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
