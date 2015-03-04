/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.cayenne;

import org.apache.cayenne.LifecycleListener;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.reflect.LifecycleCallbackRegistry;
import org.haikuos.haikudepotserver.dataobjects.PkgVersionLocalization;
import org.haikuos.haikudepotserver.naturallanguage.NaturalLanguageOrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * <p>This will listen for changes to localization and will then register that
 * the natural language is in-use with the
 * {@link org.haikuos.haikudepotserver.naturallanguage.NaturalLanguageOrchestrationService}.
 * This is essentially a cache update mechanism.</p>
 */

public class PkgVersionLocalizationNaturalLanguageUsageListener implements LifecycleListener {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgVersionLocalizationNaturalLanguageUsageListener.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    NaturalLanguageOrchestrationService naturalLanguageOrchestrationService;

    @PostConstruct
    public void init() {
        LifecycleCallbackRegistry callbackRegistry = serverRuntime.getDataDomain().getEntityResolver().getCallbackRegistry();
        callbackRegistry.addListener(PkgVersionLocalization.class, this);
    }

    private void trigger(PkgVersionLocalization pkgVersionLocalization) {

        // note that this logic may need to be distributed if more than one instance of the application
        // server were deployed in a cluster.  This is because the cache is local to the VM in the
        // NaturalLanguageOrchestrationService.

        String naturalLanguageCode = pkgVersionLocalization.getNaturalLanguage().getCode();
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
        trigger((PkgVersionLocalization) entity);
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
        trigger((PkgVersionLocalization) entity);
    }

    @Override
    public void postLoad(Object entity) {
    }

}
