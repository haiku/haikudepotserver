/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.cayenne;

import org.apache.cayenne.LifecycleListener;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.reflect.LifecycleCallbackRegistry;
import org.haikuos.haikudepotserver.dataobjects.UserRating;
import org.haikuos.haikudepotserver.naturallanguage.NaturalLanguageOrchestrationService;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * <p>This will listen for changes to user ratings and will then register that
 * the natural language is in-use with the
 * {@link org.haikuos.haikudepotserver.naturallanguage.NaturalLanguageOrchestrationService}.
 * This is essentially a cache update mechanism.</p>
 */

public class UserRatingNaturalLanguageUsageListener implements LifecycleListener {

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    NaturalLanguageOrchestrationService naturalLanguageOrchestrationService;

    @PostConstruct
    public void init() {
        LifecycleCallbackRegistry callbackRegistry = serverRuntime.getDataDomain().getEntityResolver().getCallbackRegistry();
        callbackRegistry.addListener(UserRating.class, this);
    }

    private void trigger(UserRating userRating) {

        // note that this logic may need to be distributed if more than one instance of the application
        // server were deployed in a cluster.  This is because the cache is local to the VM in the
        // NaturalLanguageOrchestrationService.

        String naturalLanguageCode = userRating.getNaturalLanguage().getCode();
        naturalLanguageOrchestrationService.setHasUserRating(naturalLanguageCode);
    }

    @Override
    public void postAdd(Object entity) {
    }

    @Override
    public void prePersist(Object entity) {
    }

    @Override
    public void postPersist(Object entity) {
        trigger((UserRating) entity);
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
        trigger((UserRating) entity);
    }

    @Override
    public void postLoad(Object entity) {
    }

}
