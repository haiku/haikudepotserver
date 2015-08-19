/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.ObjectIdQuery;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haiku.haikudepotserver.dataobjects.auto._Repository;
import org.haiku.haikudepotserver.dataobjects.support.AbstractDataObject;
import org.haiku.haikudepotserver.dataobjects.support.Coded;
import org.haiku.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.springframework.web.util.UriComponentsBuilder;

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

    // This approach of getting them all and then filtering them may not work if there are
    // quite a large number of repositories, but this eventuality is unlikely.

    public static Optional<Repository> getByCode(ObjectContext context, String code) {
        return getAll(context)
                .stream()
                .filter(r -> r.getCode().equals(code))
                .collect(SingleCollector.optional());
    }

    /**
     * <p>Returns all active repositories.</p>
     */

    public static List<Repository> getAll(ObjectContext context) {
        SelectQuery query = new SelectQuery(
                Repository.class,
                ExpressionFactory.matchExp(Repository.ACTIVE_PROPERTY, Boolean.TRUE));

        query.setCacheStrategy(QueryCacheStrategy.SHARED_CACHE);
        query.setCacheGroups(HaikuDepot.CacheGroup.REPOSITORY.name());

        return context.performQuery(query);
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
            if(!AbstractDataObject.CODE_PATTERN.matcher(getCode()).matches()) {
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

    public UriComponentsBuilder appendPathSegments(UriComponentsBuilder builder) {
        return builder.pathSegment(getCode());
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
