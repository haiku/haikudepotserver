/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects.support;

import java.util.Date;

/**
 * <p>This is an interface for objects that are capable of storing and providing a create and modify timestamp.  A
 * listener is then able to operate on such objects observing changes and thereby updating the modify timestamp on
 * the instance of the object in question.  This avoids the need to manually maintain these modify timestamps.</p>
 */

public interface CreateAndModifyTimestamped {

    public void setCreateTimestamp(Date createTimestamp);
    public Date getCreateTimestamp();
    public void setModifyTimestamp(Date modifyTimestamp);
    public Date getModifyTimestamp();

}
