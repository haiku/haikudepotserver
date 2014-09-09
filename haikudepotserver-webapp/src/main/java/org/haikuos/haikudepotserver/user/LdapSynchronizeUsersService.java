/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.user;

import org.haikuos.haikudepotserver.user.model.LdapSynchronizeUsersJob;

/**
 * <p>Implementations of this service are able to synchronize user data into an LDAP directory.</p>
 */

public interface LdapSynchronizeUsersService {

    public void submit(final LdapSynchronizeUsersJob job);

}
