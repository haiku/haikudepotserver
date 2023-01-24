/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.cayenne;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.CayenneDataObject;
import org.apache.cayenne.LifecycleListener;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.reflect.LifecycleCallbackRegistry;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>This listener can be configured with a query cache group and when something
 * happens to an object, it can drop that cache group.</p>
 */

public class QueryCacheRemoveGroupListener implements LifecycleListener {

    protected static Logger LOGGER = LoggerFactory.getLogger(QueryCacheRemoveGroupListener.class);

    private final ServerRuntime serverRuntime;

    private final List<String> groups;

    private final List<Class<? extends CayenneDataObject>> entityClasses;

    public QueryCacheRemoveGroupListener(
            ServerRuntime serverRuntime,
            Class<? extends CayenneDataObject> entityClasses,
            String group) {
        this(serverRuntime, Collections.singletonList(entityClasses), Collections.singletonList(group));
    }

    public QueryCacheRemoveGroupListener(
            ServerRuntime serverRuntime,
            List<Class<? extends CayenneDataObject>> entityClasses,
            List<String> groups) {
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(entityClasses), "entity classes must be provided");
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(groups), "groups must be provided");
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.entityClasses = entityClasses;
        this.groups = groups;
    }

    @PostConstruct
    public void init() {
        LifecycleCallbackRegistry callbackRegistry = serverRuntime.getDataDomain().getEntityResolver().getCallbackRegistry();

        for (Class<?> entityClass : entityClasses) {
            callbackRegistry.addListener(entityClass, this);
        }

        {
            StringBuilder msg = new StringBuilder();

            msg.append("changes in entities (");
            msg.append(String.join(",", entityClasses.stream().map(Class::getSimpleName).collect(Collectors.toList())));

            msg.append(") remove group caches (");
            msg.append(String.join(",",groups));
            msg.append(")");

            LOGGER.info(msg.toString());
        }
    }

    private void registerGroups(Object entity) {
        if(CayenneDataObject.class.isAssignableFrom(entity.getClass())) {

            CayenneDataObject cdo = (CayenneDataObject) entity;
            ObjectContext context = cdo.getObjectContext();

            if (null == context) {
                throw new IllegalStateException("an entity was encountered with no context");
            }

            @SuppressWarnings("unchecked")
            Set<String> contextGroups = (Set<String>) context.getUserProperty(
                    QueryCacheRemoveGroupDataChannelFilter.KEY_QUERYCACHEREMOVEGROUPS);

            if (null == contextGroups) {
                contextGroups = new HashSet<>();
                context.setUserProperty(QueryCacheRemoveGroupDataChannelFilter.KEY_QUERYCACHEREMOVEGROUPS, contextGroups);
            }

            contextGroups.addAll(groups);

        }
    }

    // ---------------------
    // LIFECYCLE LISTENER

    @Override
    public void postAdd(Object entity) {
    }

    @Override
    public void prePersist(Object entity) {
        registerGroups(entity);
    }

    @Override
    public void postPersist(Object entity) {
    }

    @Override
    public void preRemove(Object entity) {
        registerGroups(entity);
    }

    @Override
    public void postRemove(Object entity) {
    }

    @Override
    public void preUpdate(Object entity) {
        registerGroups(entity);
    }

    @Override
    public void postUpdate(Object entity) {
    }

    @Override
    public void postLoad(Object entity) {
    }

}
