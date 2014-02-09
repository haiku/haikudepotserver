/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.ObjectIdQuery;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.dataobjects.auto._Repository;
import org.haikuos.haikudepotserver.dataobjects.support.Coded;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;

import java.util.List;

public class Repository extends _Repository implements CreateAndModifyTimestamped, Coded {

    public static Repository get(ObjectContext context, ObjectId objectId) {
        return Iterables.getOnlyElement((List<Repository>) context.performQuery(new ObjectIdQuery(objectId)));
    }

    public static Optional<Repository> getByCode(ObjectContext context, String code) {
        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<Repository>) context.performQuery(new SelectQuery(
                    Repository.class,
                    ExpressionFactory.matchExp(Repository.CODE_PROPERTY, code))),
                null));
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null != getCode()) {
            if(!CODE_PATTERN.matcher(getCode()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this,CODE_PROPERTY,"malformed"));
            }
        }

    }

}
