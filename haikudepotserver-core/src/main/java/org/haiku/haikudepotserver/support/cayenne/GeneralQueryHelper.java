/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.cayenne;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.DateTimeHelper;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

public class GeneralQueryHelper {

    public static Date getLastModifyTimestampSecondAccuracy(
            ObjectContext objectContext,
            Class<? extends CreateAndModifyTimestamped> ... klass) {
        return Arrays.stream(klass)
                .flatMap(k ->
                        objectContext.select(ObjectSelect.query(k))
                                .stream()
                                .map(CreateAndModifyTimestamped::getModifyTimestamp)
                )
                .sorted(Comparator.reverseOrder())
                .findFirst()
                .map(DateTimeHelper::secondAccuracyDate)
                .orElse(new java.sql.Timestamp(0L));

    }

}
