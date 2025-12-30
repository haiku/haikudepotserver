/*
 * Copyright 2013-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.cayenne;

import com.google.common.base.Preconditions;
import org.apache.cayenne.LifecycleListener;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.reflect.LifecycleCallbackRegistry;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.dataobjects.support.MutableCreateAndModifyTimestamped;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * <p>This automates the configuration of the create and modify timestamps against certain
 * entities that support the {@link MutableCreateAndModifyTimestamped} interface.</p>
 */

@Component
public class PostAddMutableCreateAndModifyTimestampListener implements LifecycleListener {

    private final ServerRuntime serverRuntime;

    public PostAddMutableCreateAndModifyTimestampListener(ServerRuntime serverRuntime) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
    }

    @PostConstruct
    public void init() {
        LifecycleCallbackRegistry callbackRegistry = serverRuntime.getDataDomain().getEntityResolver()
                .getCallbackRegistry();

        // load in the create / modify timestamp listener -- was in the model file, but moved here for consistency
        callbackRegistry.addListener(Pkg.class, this);
        callbackRegistry.addListener(PkgScreenshot.class, this);
        callbackRegistry.addListener(PkgVersion.class, this);
        callbackRegistry.addListener(Repository.class, this);
        callbackRegistry.addListener(RepositorySourceMirror.class, this);
        callbackRegistry.addListener(User.class, this);
        callbackRegistry.addListener(UserRating.class, this);
        callbackRegistry.addListener(PkgVersionLocalization.class, this);
        callbackRegistry.addListener(PkgLocalization.class, this);
        callbackRegistry.addListener(PkgChangelog.class, this);
        callbackRegistry.addListener(PkgSupplement.class, this);
        callbackRegistry.addListener(PkgSupplementModification.class, this);
        callbackRegistry.addListener(Job.class, this);
        callbackRegistry.addListener(JobData.class, this);
    }

    @Override
    public void postAdd(Object entity) {
        MutableCreateAndModifyTimestamped mutableCreateAndModifyTimestamped = (MutableCreateAndModifyTimestamped) entity;
        mutableCreateAndModifyTimestamped.setCreateTimestamp();
        mutableCreateAndModifyTimestamped.setModifyTimestamp();
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
        MutableCreateAndModifyTimestamped mutableCreateAndModifyTimestamped = (MutableCreateAndModifyTimestamped) entity;
        mutableCreateAndModifyTimestamped.setModifyTimestamp();
    }

    @Override
    public void postUpdate(Object entity) {
    }

    @Override
    public void postLoad(Object entity) {
    }

}
