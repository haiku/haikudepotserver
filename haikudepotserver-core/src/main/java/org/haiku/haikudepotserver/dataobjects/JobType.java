/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.auto._JobType;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;

import java.util.Optional;

public class JobType extends _JobType {

    private static final long serialVersionUID = 1L;

    public static Optional<JobType> tryGetByCode(ObjectContext context, String code) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));

        return Optional.ofNullable(ObjectSelect.query(JobType.class).where(CODE.eq(code))
                .selectOne(context));
    }

    public static JobType getByCode(ObjectContext context, String code) {
        return tryGetByCode(context, code)
                .orElseThrow(() -> new ObjectNotFoundException(JobType.class.getSimpleName(), code));
    }

}
