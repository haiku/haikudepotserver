/*
 * Copyright 2013-2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects.support;

import java.sql.Timestamp;
import java.time.Clock;

/**
 * <p>This is an interface for objects that are capable of storing and providing a create and modify timestamp.  A
 * listener is then able to operate on such objects observing changes and thereby updating the modify timestamp on
 * the instance of the object in question.  This avoids the need to manually maintain these modify timestamps.</p>
 */

public interface MutableCreateAndModifyTimestamped extends CreateAndModifyTimestamped {

    void setCreateTimestamp(java.sql.Timestamp createTimestamp);

    default void setCreateTimestamp() {
        setCreateTimestamp(new Timestamp(Clock.systemUTC().millis()));
    }

    void setModifyTimestamp(java.sql.Timestamp modifyTimestamp);

    default void setModifyTimestamp() {
        setModifyTimestamp(new java.sql.Timestamp(Clock.systemUTC().millis()));
    }

}
