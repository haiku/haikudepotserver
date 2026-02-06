/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.auto._JobDataMediaType;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;

import java.io.Serial;
import java.util.Optional;

public class JobDataMediaType extends _JobDataMediaType {

    @Serial
    private static final long serialVersionUID = 1L;

    public static Optional<JobDataMediaType> tryGetByCode(ObjectContext context, String code) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));

        return Optional.ofNullable(ObjectSelect.query(JobDataMediaType.class).where(CODE.eq(code))
                .selectOne(context));
    }

    public static JobDataMediaType getByCode(ObjectContext context, String code) {
        return tryGetByCode(context, code)
                .orElseThrow(() -> new ObjectNotFoundException(JobDataMediaType.class.getSimpleName(), code));
    }

}
