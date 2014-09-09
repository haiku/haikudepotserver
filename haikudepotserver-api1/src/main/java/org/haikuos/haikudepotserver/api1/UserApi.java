/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haikuos.haikudepotserver.api1.model.user.*;
import org.haikuos.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.api1.support.ValidationException;

/**
 * <p>This interface defines operations that can be undertaken around users.</p>
 */

@JsonRpcService("/api/v1/user")
public interface UserApi {

    /**
     * <P>This method will synchronize user data with external systems; such as LDAP servers.</P>
     */

    SynchronizeUsersResult synchronizeUsers(SynchronizeUsersRequest synchronizeUsersRequest);

    /**
     * <p>This method will update the user based on the data in the request.  Only the data which is included
     * in the filter will be updated.</p>
     */

    UpdateUserResult updateUser(UpdateUserRequest updateUserRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will create a user in the system.  It is identified by a username
     * and authenticated by a password.  The password is supplied in the clear.  This
     * method will throw {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException}
     * in the case that the referenced 'natural language' is not able to be found.</p>
     */

    CreateUserResult createUser(CreateUserRequest createUserRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will get the user identified by the nickname in the request object.
     * If no user was able to be found an instance of {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException}
     * is thrown.</p>
     */

    GetUserResult getUser(GetUserRequest getUserRequest) throws ObjectNotFoundException, AuthorizationFailureException;

    /**
     * <p>This method will allow a client to authenticate against the server.  If this is
     * successful then it will return a json web token that can be used for further API
     * calls for some period of time.  If it is unsuccessful then it will return null.
     * </p>
     */

    AuthenticateUserResult authenticateUser(AuthenticateUserRequest authenticateUserRequest);

    /**
     * <p>This method will renew the token supplied.  If the token has expired then this
     * method will return a null value for the token.</p>
     */

    RenewTokenResult renewToken(RenewTokenRequest renewTokenRequest);

    /**
     * <p>This method will allow the client to modify the password of a user.</p>
     */

    ChangePasswordResult changePassword(ChangePasswordRequest changePasswordRequest) throws ObjectNotFoundException, AuthorizationFailureException, ValidationException;

    /**
     * <p>This method will allow a search for users.</p>
     */

    SearchUsersResult searchUsers(SearchUsersRequest searchUsersRequest);

    /**
     * <p>This method will kick-off a process to reset a user's password by email.  The user will be sent
     * an email containing a URL.  They will then click on the URL which will take them to a page allowing
     * them to reset their password.</p>
     */

    InitiatePasswordResetResult initiatePasswordReset(InitiatePasswordResetRequest initiatePasswordResetRequest);

    /**
     * <p>This method will complete the password reset process by taking the token and a new password then
     * configuring that password on the user.</p>
     */

    CompletePasswordResetResult completePasswordReset(CompletePasswordResetRequest completePasswordResetRequest);

}
