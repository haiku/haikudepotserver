/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.auto._JobDataType;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;

import java.io.Serial;
import java.util.Optional;

public class JobDataType extends _JobDataType {

    public final static String CODE_SUPPLIED = "supplied";
    public final static String CODE_GENERATED = "generated";

    @Serial
    private static final long serialVersionUID = 1L;

    public static Optional<JobDataType> tryGetByCode(ObjectContext context, String code) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));

        return Optional.ofNullable(ObjectSelect.query(JobDataType.class).where(CODE.eq(code))
                .selectOne(context));
    }

    public static JobDataType getByCode(ObjectContext context, String code) {
        return tryGetByCode(context, code)
                .orElseThrow(() -> new ObjectNotFoundException(JobDataEncoding.class.getSimpleName(), code));
    }

    public static JobDataType getSupplied(ObjectContext context) {
        return getByCode(context, CODE_SUPPLIED);
    }

    public static JobDataType getGenerated(ObjectContext context) {
        return getByCode(context, CODE_GENERATED);
    }
}
