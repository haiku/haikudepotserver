/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.ldap;

import com.google.common.base.Strings;
import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.apache.directory.ldap.client.api.LdapConnectionPool;
import org.apache.directory.ldap.client.api.PoolableLdapConnectionFactory;
import org.springframework.beans.factory.FactoryBean;

/**
 * <p>This factory will create a connection pool for LDAP connections.  It will produce a holder object that
 * either returns an {@link org.apache.directory.ldap.client.api.LdapConnectionPool} or will return null
 * depending on the configuration of the LDAP server.</p>
 */

// WARNING; NOT IN ACTIVE USE

public class LdapConnectionPoolFactory implements FactoryBean<LdapConnectionPoolHolder> {

    private String host;
    private Integer port;
    private String userDn;
    private String password;

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public void setUserDn(String userDn) {
        this.userDn = userDn;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    private boolean isConfigured() {
        return !Strings.isNullOrEmpty(host);
    }

    @Override
    public LdapConnectionPoolHolder getObject() throws Exception {

        if(isConfigured()) {

            LdapConnectionConfig config = new LdapConnectionConfig();
            config.setLdapHost(host);
            config.setLdapPort(null == port ? 398 : port.intValue());
            config.setName(userDn);
            config.setCredentials(password);

            PoolableLdapConnectionFactory factory = new PoolableLdapConnectionFactory(config);
            LdapConnectionPool pool = new LdapConnectionPool( factory );
            pool.setTestOnBorrow( true );

            return new LdapConnectionPoolHolder(pool);
        }

        return new LdapConnectionPoolHolder();
    }

    @Override
    public Class<?> getObjectType() {
        return LdapConnectionPoolHolder.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

}