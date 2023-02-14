/*
 * Copyright 2013-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import org.haiku.haikudepotserver.api1.model.user.AgreeUserUsageConditionsRequest;
import org.haiku.haikudepotserver.api1.model.user.AgreeUserUsageConditionsResult;
import org.haiku.haikudepotserver.api1.model.user.AuthenticateUserRequest;
import org.haiku.haikudepotserver.api1.model.user.AuthenticateUserResult;
import org.haiku.haikudepotserver.api1.model.user.CreateUserRequest;
import org.haiku.haikudepotserver.api1.model.user.CreateUserResult;
import org.haiku.haikudepotserver.api1.model.user.GetUserRequest;
import org.haiku.haikudepotserver.api1.model.user.GetUserResult;
import org.haiku.haikudepotserver.api1.model.user.GetUserUsageConditionsRequest;
import org.haiku.haikudepotserver.api1.model.user.GetUserUsageConditionsResult;

/**
 * <p>This interface defines operations that can be undertaken around users.</p>
 */

@Deprecated
public interface UserApi {

    /**
     * <p>This method will create a user in the system.  It is identified by a username
     * and authenticated by a password.  The password is supplied in the clear.  This
     * method will throw ObjectNotFoundException
     * in the case that the referenced 'natural language' is not able to be found.</p>
     */

    @Deprecated
    CreateUserResult createUser(CreateUserRequest createUserRequest);

    /**
     * <p>This method will get the user identified by the nickname in the request object.
     * If no user was able to be found an instance of ObjectNotFoundException
     * is thrown.</p>
     */
    @Deprecated
    GetUserResult getUser(GetUserRequest getUserRequest);

    /**
     * <p>This method will allow a client to authenticate against the server.  If this is
     * successful then it will return a json web token that can be used for further API
     * calls for some period of time.  If it is unsuccessful then it will return null.
     * </p>
     */
    @Deprecated
    AuthenticateUserResult authenticateUser(AuthenticateUserRequest authenticateUserRequest);

    /**
     * <p>This method will allow the user to, at any time, agree to the terms
     * and conditions.  This may be required for example when the terms change
     * and the user has agreed to some older terms and conditions.</p>
     * @since 2019-03-15
     */
    @Deprecated
    AgreeUserUsageConditionsResult agreeUserUsageConditions(AgreeUserUsageConditionsRequest request);

    /**
     * <p>This method will return details for the user usage agreement that is
     * identifier in the request.</p>
     * @since 2019-03-15
     */
    @Deprecated
    GetUserUsageConditionsResult getUserUsageConditions(GetUserUsageConditionsRequest request);

}
