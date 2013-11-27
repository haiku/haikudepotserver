/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haikuos.haikudepotserver.api1.model.user.*;
import org.haikuos.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;

/**
 * <p>This interface defines operations that can be undertaken around users.</p>
 */

@JsonRpcService("/api/v1/user")
public interface UserApi {

    /**
     * <p>This method will create a user in the system.  It is identified by a username
     * and authenticated by a password.  The password is supplied in the clear.</p>
     */

    CreateUserResult createUser(CreateUserRequest createUserRequest);

    /**
     * <p>This method will get the user identified by the nickname in the request object.
     * If no user was able to be found an instance of {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException}
     * is thrown.</p>
     */

    GetUserResult getUser(GetUserRequest getUserRequest) throws ObjectNotFoundException, AuthorizationFailureException;

    /**
     * <p>This method will allow a client to authenticate against the server.  If this is
     * successful then the client will know that it is OK to use the authentication
     * principal and credentials for further API calls.</p>
     */

    AuthenticateUserResult authenticateUser(AuthenticateUserRequest authenticateUserRequest);

}
