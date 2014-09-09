/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.user;

import org.haikuos.haikudepotserver.user.model.LdapSynchronizeUsersJob;

/**
 * <P>No-operation implementation of this service for testing purposes.</P>
 */

public class NoopLdapSynchronizeUsersService implements LdapSynchronizeUsersService {

    @Override
    public void submit(LdapSynchronizeUsersJob job) {
    }

}
