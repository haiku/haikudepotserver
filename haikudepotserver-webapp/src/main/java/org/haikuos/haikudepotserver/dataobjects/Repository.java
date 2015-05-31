/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

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
import org.haikuos.haikudepotserver.support.SingleCollector;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

public class Repository extends _Repository implements CreateAndModifyTimestamped, Coded, Comparable<Repository> {

    /**
     * <p>Prior to mid 2015 there was no 'proper' concept of a repository and so to allow for clients
     * continuing to use the API without specifying a depot, this one can be used as a fallback.</p>
     */

    public final static String CODE_DEFAULT = "haikuports";

    public static Repository get(ObjectContext context, ObjectId objectId) {
        return ((List<Repository>) context.performQuery(new ObjectIdQuery(objectId))).stream().collect(SingleCollector.single());
    }

    public static Optional<Repository> getByCode(ObjectContext context, String code) {
        return ((List<Repository>) context.performQuery(new SelectQuery(
                    Repository.class,
                    ExpressionFactory.matchExp(Repository.CODE_PROPERTY, code)))).stream().collect(SingleCollector.optional());
    }

    /**
     * <p>Returns all active repositories.</p>
     */

    public static List<Repository> getAll(ObjectContext context) {
        return context.performQuery(new SelectQuery(
                Repository.class,
                ExpressionFactory.matchExp(Repository.ACTIVE_PROPERTY, Boolean.TRUE)));
    }

    @Override
    public void validateForInsert(ValidationResult validationResult) {
        if(null==getActive()) {
            setActive(Boolean.TRUE);
        }

        super.validateForInsert(validationResult);
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null != getCode()) {
            if(!CODE_PATTERN.matcher(getCode()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this,CODE_PROPERTY,"malformed"));
            }
        }

        if(null != getInformationUrl()) {
            try {
                new URL(getInformationUrl());
            }
            catch(MalformedURLException mue) {
                validationResult.addFailure(new BeanValidationFailure(this,INFORMATION_URL_PROPERTY,"malformed"));
            }
        }

    }

    @Override
    public int compareTo(Repository o) {
        return getCode().compareTo(o.getCode());
    }

    @Override
    public String toString() {
      return "repo;"+getCode();
    }

}
