/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.cayenne;

import com.google.common.base.Preconditions;
import org.apache.cayenne.LifecycleListener;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.reflect.LifecycleCallbackRegistry;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.user.LdapException;
import org.haiku.haikudepotserver.user.LdapSynchronizationService;
import org.haiku.haikudepotserver.user.model.UserService;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * <p>This will attempt to listen to changes to a user and then after those changes have been
 * persisted, to look them up and to relay those into an LDAP server.</p>
 */

// WARNING; NOT IN ACTIVE USE

public class LdapUserUpdateListener implements LifecycleListener {

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private UserService userService;

    @Resource
    private LdapSynchronizationService ldapSynchronizationService;

    @PostConstruct
    public void init() {
        LifecycleCallbackRegistry callbackRegistry = serverRuntime.getDataDomain().getEntityResolver().getCallbackRegistry();
        callbackRegistry.addListener(User.class, this);
    }

    private void triggerUpdateUser(Object entity) {
        Preconditions.checkNotNull(entity);
        ObjectContext context = serverRuntime.getContext();
        User user = User.getByObjectId(context, ((User) entity).getObjectId());

        try {
            ldapSynchronizationService.ldapUpdateUser(context, user);
        }
        catch(LdapException le) {
            throw new RuntimeException("unable to ldap update user; " + user.toString(), le);
        }
    }

    @Override
    public void postAdd(Object entity) {
    }

    @Override
    public void prePersist(Object entity) {
    }

    @Override
    public void postPersist(Object entity) {
        triggerUpdateUser(entity);
    }

    @Override
    public void preRemove(Object entity) {
    }

    @Override
    public void postRemove(Object entity) {
    }

    @Override
    public void preUpdate(Object entity) {
    }

    @Override
    public void postUpdate(Object entity) {
        triggerUpdateUser(entity);
    }

    @Override
    public void postLoad(Object entity) {
    }

}