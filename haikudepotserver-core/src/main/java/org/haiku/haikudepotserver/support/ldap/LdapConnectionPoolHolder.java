/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.ldap;

import org.apache.directory.ldap.client.api.LdapConnectionPool;

/**
 * <p>This 'holder' can either hold an {@link org.apache.directory.ldap.client.api.LdapConnectionPool} or it
 * can hold null.</p>
 */

public class LdapConnectionPoolHolder {

    private LdapConnectionPool connectionPool;

    public LdapConnectionPoolHolder() {
    }

    public LdapConnectionPoolHolder(LdapConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    public LdapConnectionPool get() {
        return connectionPool;
    }

}
