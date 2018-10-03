/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects.support;

public interface CreateAndModifyTimestamped {
    java.sql.Timestamp getCreateTimestamp();
    java.sql.Timestamp getModifyTimestamp();
}
