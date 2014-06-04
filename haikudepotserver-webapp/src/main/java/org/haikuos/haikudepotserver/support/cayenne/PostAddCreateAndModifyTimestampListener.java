/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.cayenne;

import org.apache.cayenne.LifecycleListener;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.reflect.LifecycleCallbackRegistry;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Date;

/**
 * <p>This automates the configuration of the create and modify timestamps against certain
 * entities that support the {@link org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped} interface.</p>
 */

@Component
public class PostAddCreateAndModifyTimestampListener implements LifecycleListener {

    @Resource
    ServerRuntime serverRuntime;

    @PostConstruct
    public void init() {
        LifecycleCallbackRegistry callbackRegistry = serverRuntime.getDataDomain().getEntityResolver().getCallbackRegistry();

        // load in the create / modify timestamp listener -- was in the model file, but moved here for consistency
        callbackRegistry.addListener(Pkg.class, this);
        callbackRegistry.addListener(PkgScreenshot.class, this);
        callbackRegistry.addListener(PkgVersion.class, this);
        callbackRegistry.addListener(Publisher.class, this);
        callbackRegistry.addListener(Repository.class, this);
        callbackRegistry.addListener(User.class, this);
        callbackRegistry.addListener(UserRating.class, this);
    }

    @Override
    public void postAdd(Object entity) {
        CreateAndModifyTimestamped createAndModifyTimestamped = (CreateAndModifyTimestamped) entity;
        Date now = new Date();
        createAndModifyTimestamped.setCreateTimestamp(now);
        createAndModifyTimestamped.setModifyTimestamp(now);
    }

    @Override
    public void prePersist(Object entity) {
    }

    @Override
    public void postPersist(Object entity) {
    }

    @Override
    public void preRemove(Object entity) {
    }

    @Override
    public void postRemove(Object entity) {
    }

    @Override
    public void preUpdate(Object entity) {
        CreateAndModifyTimestamped createAndModifyTimestamped = (CreateAndModifyTimestamped) entity;
        createAndModifyTimestamped.setModifyTimestamp(new Date());
    }

    @Override
    public void postUpdate(Object entity) {
    }

    @Override
    public void postLoad(Object entity) {
    }

}