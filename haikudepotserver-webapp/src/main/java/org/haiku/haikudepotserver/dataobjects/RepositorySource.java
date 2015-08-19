/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.ObjectIdQuery;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haiku.haikudepotserver.dataobjects.auto._RepositorySource;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.haiku.haikudepotserver.support.cayenne.ExpressionHelper;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RepositorySource extends _RepositorySource {

    public static RepositorySource get(ObjectContext context, ObjectId objectId) {
        return ((List<RepositorySource>) context.performQuery(new ObjectIdQuery(objectId))).stream().collect(SingleCollector.single());
    }

    public static Optional<RepositorySource> getByCode(ObjectContext context, String code) {
        return ((List<RepositorySource>) context.performQuery(new SelectQuery(
                RepositorySource.class,
                ExpressionFactory.matchExp(RepositorySource.CODE_PROPERTY, code)))).stream().collect(SingleCollector.optional());
    }

    public static List<RepositorySource> findByRepository(ObjectContext context, Repository repository) {
        return ((List<RepositorySource>) context.performQuery(new SelectQuery(
                RepositorySource.class,
                ExpressionFactory.matchExp(RepositorySource.REPOSITORY_PROPERTY, repository)))).stream().collect(Collectors.toList());
    }

    public static List<RepositorySource> findActiveByRepository(ObjectContext context, Repository repository) {
        return ((List<RepositorySource>) context.performQuery(new SelectQuery(
                RepositorySource.class,
                ExpressionHelper.andAll(ImmutableList.of(
                        ExpressionFactory.matchExp(RepositorySource.REPOSITORY_PROPERTY, repository),
                        ExpressionFactory.matchExp(RepositorySource.ACTIVE_PROPERTY, Boolean.TRUE)
                ))
                )).stream().collect(Collectors.toList()));
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

        if(null != getUrl()) {
            try {
                new URL(getUrl());
            }
            catch(MalformedURLException mue) {
                validationResult.addFailure(new BeanValidationFailure(this,URL_PROPERTY,"malformed"));
            }
        }

    }

    /**
     * <p>This is the URL at which one might find the packages for this repository.</p>
     */

    public URL getPackagesBaseURL() {
        URL base = getBaseURL();

        try {
            return new URL(
                    base.getProtocol(),
                    base.getHost(),
                    base.getPort(),
                    base.getPath() + "/packages");
        }
        catch(MalformedURLException mue) {
            throw new IllegalStateException("unable to reform a url",mue);
        }

    }

    /**
     * <p>Other files are able to be found in the repository.  This method provides a root for these
     * files.</p>
     */

    private URL getBaseURL() {
        URL url;

        try {
            url = new URL(getUrl());
        }
        catch(MalformedURLException mue) {
            throw new IllegalStateException("malformed url should not be stored in a repository");
        }

        String path = url.getPath();

        if(Strings.isNullOrEmpty(path)) {
            throw new IllegalStateException("malformed url; no path component to the hpkr data");
        }

        int lastSlash = path.lastIndexOf('/');

        if(lastSlash == path.length()-1) {
            throw new IllegalStateException("malformed url; no path component to the hpkr data");
        }
        else {
            if(-1==lastSlash) {
                path = "";
            }
            else {
                path = path.substring(0,lastSlash);
            }
        }

        try {
            return new URL(
                    url.getProtocol(),
                    url.getHost(),
                    url.getPort(),
                    path);
        }
        catch(MalformedURLException mue) {
            throw new IllegalStateException("unable to reform a url",mue);
        }
    }

    @Override
    public String toString() {
        return "reposrc;" + getCode();
    }

}
