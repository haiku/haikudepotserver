/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.cayenne;

import com.google.common.base.Preconditions;
import org.apache.cayenne.LifecycleListener;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.reflect.LifecycleCallbackRegistry;
import org.haikuos.haikudepotserver.dataobjects.UserRating;
import org.haikuos.haikudepotserver.support.job.JobOrchestrationService;
import org.haikuos.haikudepotserver.userrating.model.UserRatingDerivationJobSpecification;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * <p>This listener will detect changes in the user rating entities and will then trigger a process (probably
 * async) that is able to update the derived user rating on the package involved.</p>
 */

public class UserRatingDerivationTriggerListener implements LifecycleListener {

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private JobOrchestrationService jobOrchestrationService;

    @PostConstruct
    public void init() {
        LifecycleCallbackRegistry callbackRegistry = serverRuntime.getDataDomain().getEntityResolver().getCallbackRegistry();
        callbackRegistry.addListener(UserRating.class, this);
    }

    private void triggerUpdateUserRatingDerivationForAssociatedPackage(Object entity) {
        Preconditions.checkNotNull(entity);
        UserRating userRating = (UserRating) entity;
        String pkgName = userRating.getPkgVersion().getPkg().getName();
        jobOrchestrationService.submit(
                new UserRatingDerivationJobSpecification(pkgName),
                JobOrchestrationService.CoalesceMode.QUEUED);
    }

    @Override
    public void postAdd(Object entity) {
    }

    @Override
    public void prePersist(Object entity) {
    }

    @Override
    public void postPersist(Object entity) {
        triggerUpdateUserRatingDerivationForAssociatedPackage(entity);
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
        triggerUpdateUserRatingDerivationForAssociatedPackage(entity);
    }

    @Override
    public void postLoad(Object entity) {
    }

}
