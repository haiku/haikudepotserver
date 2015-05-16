/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.cayenne;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.CayenneDataObject;
import org.apache.cayenne.LifecycleListener;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.reflect.LifecycleCallbackRegistry;
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

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private QueryCacheRemoveGroupDataChannelFilter dataChannelFilter;

    private List<String> groups;

    private List<Class<CayenneDataObject>> entityClasses;

    public QueryCacheRemoveGroupListener() {
        super();
    }

    public void setEntityClass(Class<CayenneDataObject> entityClass) {
        this.entityClasses = Collections.singletonList(entityClass);
    }

    public void setEntityClasses(List<Class<CayenneDataObject>> entityClasses) {
        this.entityClasses = entityClasses;
    }

    public void setGroup(String group) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        this.groups = Collections.singletonList(group);
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    @PostConstruct
    public void init() {
        Preconditions.checkState(null!=entityClasses && !entityClasses.isEmpty(), "the entity classes must be provided");
        LifecycleCallbackRegistry callbackRegistry = serverRuntime.getDataDomain().getEntityResolver().getCallbackRegistry();

        for(Class entityClass : entityClasses) {
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

            if(null==context) {
                throw new IllegalStateException("an entity was encountered with no context");
            }

            @SuppressWarnings("unchecked")
            Set<String> contextGroups = (Set<String>) context.getUserProperty(QueryCacheRemoveGroupDataChannelFilter.KEY_QUERYCACHEREMOVEGROUPS);

            if(null==contextGroups) {
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
