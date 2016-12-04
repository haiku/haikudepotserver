/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.ObjectIdQuery;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.haiku.haikudepotserver.dataobjects.auto._RepositorySource;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.haiku.haikudepotserver.support.cayenne.ExpressionHelper;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RepositorySource extends _RepositorySource {

    private final static Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9]{2,16}_[a-z0-9_]+$");

    public static RepositorySource get(ObjectContext context, ObjectId objectId) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != objectId, "the objectId must be supplied");
        Preconditions.checkArgument(objectId.getEntityName().equals(RepositorySource.class.getSimpleName()), "the objectId must be targetting RepositorySource");
        return ((List<RepositorySource>) context.performQuery(new ObjectIdQuery(objectId))).stream().collect(SingleCollector.single());
    }

    public static Optional<RepositorySource> getByCode(ObjectContext context, String code) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "the code must be supplied");
        return ((List<RepositorySource>) context.performQuery(new SelectQuery(
                RepositorySource.class,
                ExpressionFactory.matchExp(RepositorySource.CODE_PROPERTY, code)))).stream().collect(SingleCollector.optional());
    }

    public static List<RepositorySource> findActiveByRepository(ObjectContext context, Repository repository) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != repository, "the repository must be supplied");
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
            if(!RepositorySource.CODE_PATTERN.matcher(getCode()).matches()) {
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

        if(null != getUrl()) {
            if(getUrl().endsWith("/")) {
                validationResult.addFailure(new BeanValidationFailure(this,URL_PROPERTY,"trailingslash"));
            }
        }

    }

    /**
     * <p>This is the URL at which one might find the packages for this repository.</p>
     */

    public URL getPackagesBaseURL() {
        try {
            return UriComponentsBuilder.fromUriString(getBaseURL().toString())
                    .pathSegment("packages")
                    .build()
                    .toUri()
                    .toURL();
        }
        catch(MalformedURLException mue) {
            throw new IllegalStateException("unable to reform a url for obtaining packages",mue);
        }
    }

    /**
     * <p>This URL can be used to access the HPKR data for the repository source.</p>
     */

    public URL getHpkrURL() {
        try {
            return UriComponentsBuilder.fromUriString(getBaseURL().toString())
                    .pathSegment("repo")
                    .build()
                    .toUri()
                    .toURL();
        }
        catch(MalformedURLException mue) {
            throw new IllegalStateException("unable to reform a url for obtaining packages",mue);
        }
    }

    /**
     * <p>Other files are able to be found in the repository.  This method provides a root for these
     * files.</p>
     */

    private URL getBaseURL() {
        try {
            return new URL(getUrl());
        }
        catch(MalformedURLException mue) {
            throw new IllegalStateException("malformed url should not be stored in a repository");
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("code", getCode())
                .build();
    }

}
