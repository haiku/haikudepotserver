/*
 * Copyright 2013-2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.query.ObjectIdQuery;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.haiku.haikudepotserver.dataobjects.auto._PkgVersion;
import org.haiku.haikudepotserver.dataobjects.support.MutableCreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.haiku.haikudepotserver.support.VersionCoordinatesComparator;
import org.haiku.haikudepotserver.support.cayenne.ExpressionHelper;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PkgVersion extends _PkgVersion implements MutableCreateAndModifyTimestamped, Comparable<PkgVersion> {

    private final static Pattern MAJOR_PATTERN = Pattern.compile("^[\\w_]+$");
    private final static Pattern MINOR_PATTERN = Pattern.compile("^[\\w_]+$");
    private final static Pattern MICRO_PATTERN = Pattern.compile("^[\\w_.]+$");
    private final static Pattern PRE_RELEASE_PATTERN = Pattern.compile("^[\\w_.]+$");

    public static PkgVersion get(ObjectContext context, ObjectId objectId) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != objectId, "the objectId must be supplied");
        Preconditions.checkArgument(objectId.getEntityName().equals(PkgVersion.class.getSimpleName()), "the objectId must be targetting PkgVersion");
        return ((List<PkgVersion>) context.performQuery(new ObjectIdQuery(objectId))).stream().collect(SingleCollector.single());
    }

    public static List<PkgVersion> getForPkg(
            ObjectContext context,
            Pkg pkg,
            boolean includeInactive) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != pkg, "the pkg must be supplied");
        return getForPkg(context, pkg, null, null, includeInactive);
    }

    public static List<PkgVersion> getForPkg(
            ObjectContext context,
            Pkg pkg,
            Repository repository,
            boolean includeInactive) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != pkg, "the pkg must be supplied");
        Preconditions.checkArgument(null != repository, "a repository must be supplied to give context to obtaining a pkg version for a pkg");
        return getForPkg(context, pkg, null, repository, includeInactive);
    }

    public static List<PkgVersion> getForPkg(
            ObjectContext context,
            Pkg pkg,
            RepositorySource repositorySource,
            boolean includeInactive) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != pkg, "the pkg must be supplied");
        Preconditions.checkArgument(null != repositorySource, "a repository source must be supplied to give context to obtaining a pkg version for a pkg");
        return getForPkg(context, pkg, repositorySource, null, includeInactive);
    }

    private static List<PkgVersion> getForPkg(
            ObjectContext context,
            Pkg pkg,
            RepositorySource repositorySource,
            Repository repository,
            boolean includeInactive) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != pkg, "the pkg must be supplied");

        ObjectSelect<PkgVersion> select = ObjectSelect.query(PkgVersion.class).where(PKG.eq(pkg));

        if(!includeInactive) {
            select = select
                    .and(ACTIVE.isTrue())
                    .and(REPOSITORY_SOURCE.dot(RepositorySource.REPOSITORY).dot(Repository.ACTIVE).isTrue());
        }

        if (null != repositorySource) {
            select = select.and(PkgVersion.REPOSITORY_SOURCE.eq(repositorySource));
        }

        if (null != repository) {
            select = select.and(PkgVersion.REPOSITORY_SOURCE.dot(RepositorySource.REPOSITORY).eq(repository));
        }

        return select.select(context);
    }

    public static Optional<PkgVersion> getForPkg(
            ObjectContext context,
            Pkg pkg,
            Repository repository,
            Architecture architecture,
            VersionCoordinates versionCoordinates) {

        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != pkg, "the pkg must be supplied");
        Preconditions.checkArgument(null != architecture, "the architecture must be supplied");
        Preconditions.checkArgument(null != versionCoordinates && null!=versionCoordinates.getMajor(), "missing or malformed version coordinates");
        Preconditions.checkArgument(null != repository, "the repository is required to lookup a package version");

        return Optional.ofNullable(ObjectSelect.query(PkgVersion.class)
                .where(ExpressionHelper.toExpression(versionCoordinates))
                .and(PKG.eq(pkg))
                .and(ARCHITECTURE.eq(architecture))
                .and(REPOSITORY_SOURCE.dot(RepositorySource.REPOSITORY).eq(repository))
                .selectOne(context));
    }

    @Override
    public void validateForInsert(ValidationResult validationResult) {

        if (null == getActive()) {
            setActive(true);
        }

        if (null == getViewCounter()) {
            setViewCounter(0l);
        }

        if (null == getIsLatest()) {
            setIsLatest(false);
        }

        super.validateForInsert(validationResult);
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if (null != getMajor()) {
            if (!MAJOR_PATTERN.matcher(getMajor()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, MAJOR.getName(), "malformed"));
            }
        }

        if (null != getMinor()) {
            if (!MINOR_PATTERN.matcher(getMinor()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, MINOR.getName(), "malformed"));
            }
        }

        if (null != getMicro()) {
            if (!MICRO_PATTERN.matcher(getMicro()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, MICRO.getName(), "malformed"));
            }
        }

        if (null != getPreRelease()) {
            if (!PRE_RELEASE_PATTERN.matcher(getPreRelease()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, PRE_RELEASE.getName(), "malformed"));
            }
        }

        if (null != getRevision()) {
            if (getRevision() <= 0) {
                validationResult.addFailure(new BeanValidationFailure(this, REVISION.getName(), "lessThanEqualZero"));
            }
        }

        if (getViewCounter() < 0) {
            validationResult.addFailure(new BeanValidationFailure(this, VIEW_COUNTER.getName(), "min"));
        }

    }

    public void incrementViewCounter() {
        setViewCounter(getViewCounter() + 1);
    }

    /**
     * <p>This will try to find localized data for the pkg version for the supplied natural language.  Because
     * English language data is hard-coded into the package payload, english will always be available.</p>
     */

    public Optional<PkgVersionLocalization> getPkgVersionLocalization(final NaturalLanguage naturalLanguage) {
        return getPkgVersionLocalization(naturalLanguage.getCode());
    }

    /**
     * <p>This will try to find localized data for the pkg version for the supplied natural language.  Because
     * English language data is hard-coded into the package payload, english will always be available.</p>
     */

    public Optional<PkgVersionLocalization> getPkgVersionLocalization(final String naturalLanguageCode) {
        Preconditions.checkState(!Strings.isNullOrEmpty(naturalLanguageCode));
        return getPkgVersionLocalizations()
                .stream()
                .filter(pvl -> pvl.getNaturalLanguage().getCode().equals(naturalLanguageCode))
                .collect(SingleCollector.optional());
    }

    /**
     * <p>This method will return the localization for the language specified or will return the localization
     * for english if the desired language is not available.</p>
     */

    // have to add the 'byCode' for JSP to be able to work with it.
    public PkgVersionLocalization getPkgVersionLocalizationOrFallbackByCode(final String naturalLanguageCode) {
        Optional<PkgVersionLocalization> pkgVersionLocalizationOptional = Optional.empty();

        if (!Strings.isNullOrEmpty(naturalLanguageCode)) {
            pkgVersionLocalizationOptional = getPkgVersionLocalization(naturalLanguageCode);
        }

        if (!pkgVersionLocalizationOptional.isPresent()) {
            pkgVersionLocalizationOptional = getPkgVersionLocalization(NaturalLanguage.CODE_ENGLISH);
        }

        if (!pkgVersionLocalizationOptional.isPresent()) {
            throw new IllegalStateException("unable to find the fallback localization for " + toString());
        }

        return pkgVersionLocalizationOptional.get();
    }

    /**
     * <p>Renders the copyright entities into a list of strings.</p>
     */

    public List<String> getCopyrights() {
        return getPkgVersionCopyrights().stream().map(PkgVersionCopyright::getBody).collect(Collectors.toList());
    }

    /**
     * <p>Renders the licenses entities into a list of strings.</p>
     */

    public List<String> getLicenses() {
        return getPkgVersionLicenses().stream().map(PkgVersionLicense::getBody).collect(Collectors.toList());
    }

    public Optional<PkgVersionUrl> getPkgVersionUrlForType(final PkgUrlType type) {
        Preconditions.checkNotNull(type);
        return getPkgVersionUrls().stream().filter(pvu -> pvu.getPkgUrlType().equals(type)).collect(SingleCollector.optional());
    }

    public Optional<PkgUserRatingAggregate> getPkgUserRatingAggregate() {
        return getPkg().getPkgUserRatingAggregate(getRepositorySource().getRepository());
    }

    public Optional<Float> getDerivedAggregatedUserRating() {
        Optional<PkgUserRatingAggregate> aggregateOptional = getPkgUserRatingAggregate();

        if(aggregateOptional.isPresent()) {
            return Optional.of(aggregateOptional.get().getDerivedRating());
        }

        return Optional.empty();
    }

    public Optional<Integer> getDerivedAggregatedUserRatingSampleSize() {
        Optional<PkgUserRatingAggregate> aggregateOptional = getPkgUserRatingAggregate();

        if(aggregateOptional.isPresent()) {
            return Optional.of(aggregateOptional.get().getDerivedRatingSampleSize());
        }

        return Optional.empty();
    }

    /**
     * <p>This method will provide a URL to the actual data of the package.</p>
     */

    public Optional<URL> tryGetHpkgURL() {
        return getRepositorySource().tryGetExternalFacingPackagesBaseURL()
                .map(u -> {
                    try {
                        return new URL(
                                u.getProtocol(),
                                u.getHost(),
                                u.getPort(),
                                u.getPath() + "/" + getHpkgFilename());
                    } catch (MalformedURLException mue) {
                        throw new IllegalStateException(
                                "unable to create the URL to the hpkg data", mue);
                    }
                });
    }

    /**
     * <p>Ultimately, the HPKG file will exist on a server somewhere in the repository for access
     * by clients.  The file must have a name which is able to be derived from information about
     * the package version.  This method will provide that package version's filename for the
     * package file.</p>
     */

    public String getHpkgFilename() {

        StringBuilder builder = new StringBuilder();
        builder.append(getPkg().getName());
        builder.append('-');
        builder.append(getMajor());

        if (null != getMinor()) {
            builder.append('.');
            builder.append(getMinor());
        }

        if (null != getMicro()) {
            builder.append('.');
            builder.append(getMicro());
        }

        if (null != getPreRelease()) {
            builder.append('~');
            builder.append(getPreRelease());
        }

        if (null != getRevision()) {
            builder.append('-');
            builder.append(getRevision());
        }

        builder.append('-');
        builder.append(getArchitecture().getCode());

        builder.append(".hpkg");

        return builder.toString();
    }

    public VersionCoordinates toVersionCoordinates() {
        return new VersionCoordinates(
                getMajor(),
                getMinor(),
                getMicro(),
                getPreRelease(),
                getRevision());
    }

    public UriComponentsBuilder appendPathSegments(UriComponentsBuilder builder) {
        getPkg().appendPathSegments(builder);
        getRepositorySource().getRepository().appendPathSegments(builder);
        toVersionCoordinates().appendPathSegments(builder);
        getArchitecture().appendPathSegments(builder);
        return builder;
    }

    public String toStringWithPkgAndArchitecture() {
        return getPkg().getName() + " - " + toString() + " - " + getArchitecture().getCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("versionCoordinates", toVersionCoordinates().toString())
                .build();
    }

    @Override
    public int compareTo(PkgVersion o) {
        return ComparisonChain.start()
                .compare(getArchitecture().getCode(), o.getArchitecture().getCode())
                .compare(toVersionCoordinates(), o.toVersionCoordinates(), new VersionCoordinatesComparator())
                .result();

    }
}
