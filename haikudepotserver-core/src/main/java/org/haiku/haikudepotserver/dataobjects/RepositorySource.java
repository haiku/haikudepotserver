/*
 * Copyright 2015-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.SelectById;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.haiku.haikudepotserver.dataobjects.auto._RepositorySource;
import org.haiku.haikudepotserver.dataobjects.auto._RepositorySourceMirror;
import org.haiku.haikudepotserver.support.ExposureType;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class RepositorySource extends _RepositorySource {

    private final static Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9]{2,16}_[a-z0-9_]+$");

    public static RepositorySource get(ObjectContext context, ObjectId objectId) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != objectId, "the objectId must be supplied");
        Preconditions.checkArgument(objectId.getEntityName().equals(RepositorySource.class.getSimpleName()),
                "the objectId must be targetting RepositorySource");
        return SelectById.query(RepositorySource.class, objectId)
                .sharedCache()
                .selectOne(context);
    }

    public static RepositorySource getByCode(ObjectContext context, String code) {
        return tryGetByCode(context, code)
                .orElseThrow(() -> new ObjectNotFoundException(RepositorySource.class.getSimpleName(), code));
    }

    public static Optional<RepositorySource> tryGetByCode(ObjectContext context, String code) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "the code must be supplied");
        return Optional.ofNullable(ObjectSelect.query(RepositorySource.class).where(CODE.eq(code)).selectOne(context));
    }

    public static List<RepositorySource> findActiveByRepository(ObjectContext context, Repository repository) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != repository, "the repository must be supplied");
        return ObjectSelect.query(RepositorySource.class)
                .where(REPOSITORY.eq(repository))
                .and(ACTIVE.isTrue())
                .select(context);
    }

    UriComponentsBuilder appendPathSegments(UriComponentsBuilder builder) {
        return builder.pathSegment(getCode());
    }

    @Override
    public void validateForInsert(ValidationResult validationResult) {
        if(null==getActive()) {
            setActive(Boolean.TRUE);
        }

        super.validateForInsert(validationResult);
    }

    /**
     * <p>Note here that "url" is not actually validated as a URL because it is
     * actually an identifier.</p>
     */

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null != getCode()) {
            if(!RepositorySource.CODE_PATTERN.matcher(getCode()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, CODE.getName(), "malformed"));
            }
        }

        if (null != getIdentifier() && StringUtils.isBlank(getIdentifier())) {
            validationResult.addFailure(new BeanValidationFailure(this, IDENTIFIER.getName(), "notempty"));
        }

        validateUrl(validationResult, getForcedInternalBaseUrl(), FORCED_INTERNAL_BASE_URL.getName());
    }

    private void validateUrl(ValidationResult validationResult, String url, String propertyName) {
        if (null != url) {
            if (StringUtils.isBlank(url)) {
                validationResult.addFailure(new BeanValidationFailure(this, propertyName, "notempty"));
            }
            if (!StringUtils.isAsciiPrintable(url) || StringUtils.containsWhitespace(url)) {
                validationResult.addFailure(new BeanValidationFailure(this, propertyName, "malformed"));
            }
        }
    }

    /**
     * <p>This is the URL at which one might find the packages for this repository.</p>
     */

    public Optional<URI> tryGetPackagesBaseURI(ExposureType exposureType) {
        return tryGetBaseURI(exposureType)
                .map(uri -> UriComponentsBuilder.fromUri(uri)
                        .pathSegment("packages")
                        .build()
                        .toUri());
    }

    /**
     * <p>This URL can be used to access the HPKR data for the repository source.</p>
     */

    public Optional<URI> tryGetInternalFacingDownloadHpkrURI() {
        return tryGetInternalFacingDownloadLeafURI("repo");
    }

    /**
     * <p>This URL can be used to access the "repo.info" file for the repository.</p>
     */

    public Optional<URI> tryGetInternalFacingDownloadRepoInfoURI() {
        return tryGetInternalFacingDownloadLeafURI("repo.info");
    }

    private Optional<URI> tryGetInternalFacingDownloadLeafURI(String leaf) {
        return tryGetBaseURI(ExposureType.INTERNAL_FACING)
                .map(uri -> UriComponentsBuilder.fromUri(uri)
                        .pathSegment(leaf)
                        .build()
                        .toUri());
    }

    /**
     * <p>Other files are able to be found in the repository.  This method provides a root for these
     * files.</p>
     */

    private Optional<URI> tryGetBaseURI(ExposureType exposureType) {
        return tryGetBaseURIString(exposureType)
                .map(u -> {
                    try {
                        return new URI(u);
                    }
                    catch(URISyntaxException mue) {
                        throw new IllegalStateException("malformed url should not be stored in a repository", mue);
                    }
                });
    }

    private Optional<String> tryGetBaseURIString(ExposureType exposureType) {
        return Optional.ofNullable(Optional.ofNullable(getForcedInternalBaseUrl())
                .filter(u -> exposureType == ExposureType.INTERNAL_FACING)
                .orElseGet(() -> tryGetPrimaryMirror().map(RepositorySourceMirror::getBaseUrl).orElse(null)));
    }

    public RepositorySourceMirror getPrimaryMirror() {
        return tryGetPrimaryMirror()
                .orElseThrow(() -> new IllegalStateException(
                        "unable to get the primary mirror for [" + getCode() + "]"));
    }

    /**
     * <p>This is the master mirror or the one that the other ones should be copying from.</p>
     */

    public Optional<RepositorySourceMirror> tryGetPrimaryMirror() {
        return getRepositorySourceMirrors()
                .stream()
                .filter(_RepositorySourceMirror::getIsPrimary)
                .collect(SingleCollector.optional());
    }

    public void setLastImportTimestamp() {
        setLastImportTimestamp(
                new java.sql.Timestamp(Clock.systemUTC().millis()));
    }

    public List<String> getExtraIdentifiers() {
        return CollectionUtils.emptyIfNull(getRepositorySourceExtraIdentifiers())
                .stream()
                .map(RepositorySourceExtraIdentifier::getIdentifier)
                .sorted()
                .toList();
    }

    public Optional<RepositorySourceExtraIdentifier> tryGetRepositorySourceExtraIdentifierForIdentifier(String identifier) {
        return CollectionUtils.emptyIfNull(getRepositorySourceExtraIdentifiers())
                .stream()
                .filter(rsei -> rsei.getIdentifier().equals(identifier))
                .findFirst();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("code", getCode())
                .build();
    }

}
