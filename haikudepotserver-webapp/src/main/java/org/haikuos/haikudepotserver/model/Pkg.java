/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.model;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.SimpleValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.model.auto._Pkg;
import org.haikuos.haikudepotserver.model.support.CreateAndModifyTimestamped;

import java.util.List;
import java.util.regex.Pattern;

public class Pkg extends _Pkg implements CreateAndModifyTimestamped {

    public static Pattern NAME_PATTERN = Pattern.compile("^[^\\s/=!<>-]+$");

    public static Optional<Pkg> getByName(ObjectContext context, String name) {
        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<Pkg>) context.performQuery(new SelectQuery(
                        Pkg.class,
                        ExpressionFactory.matchExp(Pkg.NAME_PROPERTY, name))),
                null));
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null != getName()) {
            if(!NAME_PATTERN.matcher(getName()).matches()) {
                validationResult.addFailure(new SimpleValidationFailure(this,NAME_PATTERN + ".malformed"));
            }
        }

    }

}
