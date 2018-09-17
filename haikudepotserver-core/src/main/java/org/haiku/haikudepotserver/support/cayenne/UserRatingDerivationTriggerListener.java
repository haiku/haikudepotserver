/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.cayenne;

import com.google.common.base.Preconditions;
import org.apache.cayenne.CayenneDataObject;
import org.apache.cayenne.LifecycleListener;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.reflect.LifecycleCallbackRegistry;
import org.apache.commons.collections4.CollectionUtils;
import org.haiku.haikudepotserver.dataobjects.UserRating;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.userrating.model.UserRatingDerivationJobSpecification;
import org.haiku.haikudepotserver.job.model.JobService;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>This listener will detect changes in the user rating entities and will then trigger a process (probably
 * async) that is able to update the derived user rating on the package involved.</p>
 */

@Component
public class UserRatingDerivationTriggerListener implements LifecycleListener {

    private final ServerRuntime serverRuntime;

    private final JobService jobService;

    public UserRatingDerivationTriggerListener(
            ServerRuntime serverRuntime,
            JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    @PostConstruct
    public void init() {
        LifecycleCallbackRegistry callbackRegistry = serverRuntime.getDataDomain().getEntityResolver().getCallbackRegistry();
        callbackRegistry.addListener(UserRating.class, this);
    }

    private String derivePkgName(Object entity) {
        Preconditions.checkNotNull(entity);
        UserRating userRating = (UserRating) entity;
        return userRating.getPkgVersion().getPkg().getName();
    }

    private void triggerUpdateUserRatingDerivationForAssociatedPkg(Object entity) {
        triggerUpdateUserRatingDerivationForPkgName(derivePkgName(entity));
    }

    private void triggerUpdateUserRatingDerivationForPkgName(String pkgName) {
        jobService.submit(
                new UserRatingDerivationJobSpecification(pkgName),
                JobSnapshot.COALESCE_STATUSES_QUEUED);
    }

    @Override
    public void postAdd(Object entity) {
    }

    @Override
    public void prePersist(Object entity) {
    }

    @Override
    public void postPersist(Object entity) {
        triggerUpdateUserRatingDerivationForAssociatedPkg(entity);
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
        triggerUpdateUserRatingDerivationForAssociatedPkg(entity);
    }

    @Override
    public void postLoad(Object entity) {
    }

}
