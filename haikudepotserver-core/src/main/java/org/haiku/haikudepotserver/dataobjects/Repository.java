/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.SelectById;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.haiku.haikudepotserver.dataobjects.auto._Repository;
import org.haiku.haikudepotserver.dataobjects.support.AbstractDataObject;
import org.haiku.haikudepotserver.dataobjects.support.Coded;
import org.haiku.haikudepotserver.dataobjects.support.MutableCreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Repository extends _Repository implements MutableCreateAndModifyTimestamped, Coded, Comparable<Repository> {

    /**
     * <p>Prior to mid 2015 there was no 'proper' concept of a repository and so to allow for clients
     * continuing to use the API without specifying a depot, this one can be used as a fallback.</p>
     */

    public final static String CODE_DEFAULT = "haikuports";

    public static Repository get(ObjectContext context, ObjectId objectId) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != objectId, "the objectId must be supplied");
        Preconditions.checkArgument(objectId.getEntityName().equals(Repository.class.getSimpleName()),
                "the objectId must be targetting Repository");
        return SelectById.query(Repository.class, objectId).selectOne(context);
    }

    // This approach of getting them all and then filtering them may not work if there are
    // quite a large number of repositories, but this eventuality is unlikely.

    public static Optional<Repository> tryGetByCode(ObjectContext context, String code) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != code, "the code must be supplied");
        return getAll(context)
                .stream()
                .filter(r -> r.getCode().equals(code))
                .collect(SingleCollector.optional());
    }

    public static Repository getByCode(ObjectContext context, String code) {
        return tryGetByCode(context, code).orElseThrow(
                () -> new ObjectNotFoundException(Repository.class.getSimpleName(), code));
    }

    public static List<Repository> getAllActive(ObjectContext context) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        return getAll(context).stream().filter(_Repository::getActive).collect(Collectors.toList());
    }

        /**
         * <p>Returns all active repositories.</p>
         */

    public static List<Repository> getAll(ObjectContext context) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        return ObjectSelect.query(Repository.class)
                .sharedCache()
                .cacheGroup(HaikuDepot.CacheGroup.REPOSITORY.name())
                .select(context);
    }

    public Optional<RepositorySource> tryGetRepositorySourceForArchitecture(Architecture architecture) {
        if (architecture.getCode().equals(Architecture.CODE_ANY)) {
            return getRepositorySources().stream().findFirst();
        }
        return getRepositorySources().stream()
                .filter(rs -> null != rs.getArchitecture())
                .filter(rs -> rs.getArchitecture().equals(architecture))
                .collect(SingleCollector.optional());
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
                validationResult.addFailure(new BeanValidationFailure(this, CODE.getName(), "malformed"));
            }
        }

        if(null != getInformationUrl()) {
            try {
                new URI(getInformationUrl());
            }
            catch(URISyntaxException mue) {
                validationResult.addFailure(new BeanValidationFailure(this, INFORMATION_URL.getName(), "malformed"));
            }
        }

    }

    UriComponentsBuilder appendPathSegments(UriComponentsBuilder builder) {
        return builder.pathSegment(getCode());
    }

    @Override
    public int compareTo(Repository o) {
        return getCode().compareTo(o.getCode());
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("code", getCode())
                .build();
    }

}
