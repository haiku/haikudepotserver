/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.cayenne;

import org.haikuos.haikudepotserver.model.support.CreateAndModifyTimestamped;

import java.util.Date;

/**
 * <p>This automates the configuration of the create and modify timestamps against certain
 * entities that support the {@link CreateAndModifyTimestamped} interface.</p>
 */

public class PostAddCreateAndModifyTimestampListener {

    public void onPostAdd(Object entity) {
        CreateAndModifyTimestamped createAndModifyTimestamped = (CreateAndModifyTimestamped) entity;
        Date now = new Date();
        createAndModifyTimestamped.setCreateTimestamp(now);
        createAndModifyTimestamped.setModifyTimestamp(now);
    }

    public void onPreUpdate(Object entity) {
        CreateAndModifyTimestamped createAndModifyTimestamped = (CreateAndModifyTimestamped) entity;
        createAndModifyTimestamped.setModifyTimestamp(new Date());
    }

}
