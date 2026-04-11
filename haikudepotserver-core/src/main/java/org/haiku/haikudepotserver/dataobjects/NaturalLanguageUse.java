/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haiku.haikudepotserver.dataobjects.auto._NaturalLanguageUse;
import org.haiku.haikudepotserver.dataobjects.support.MutableCreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;

import java.io.Serial;
import java.util.Optional;

public class NaturalLanguageUse extends _NaturalLanguageUse implements MutableCreateAndModifyTimestamped {

    @Serial
    private static final long serialVersionUID = 1L;

    public static NaturalLanguageUse getForNaturalLanguage(ObjectContext context, NaturalLanguage naturalLanguage) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != naturalLanguage, "the `naturalLanguage` must be supplied");
        return tryGetForNaturalLanguage(context, naturalLanguage)
                .orElseThrow(() -> new ObjectNotFoundException(NaturalLanguageUse.class.getName(), naturalLanguage.getCode()));
    }

    public static Optional<NaturalLanguageUse> tryGetForNaturalLanguage(ObjectContext context, NaturalLanguage naturalLanguage) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != naturalLanguage, "the `naturalLanguage` must be supplied");
        return Optional.ofNullable(ObjectSelect.query(NaturalLanguageUse.class)
                .where(NaturalLanguageUse.NATURAL_LANGUAGE.eq(naturalLanguage))
                .selectOne(context));
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if (null != getCount()) {
            if (getCount() < 0) {
                validationResult.addFailure(new BeanValidationFailure(this, COUNT.getName(), "positive"));
            }
        }

    }

}
