/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.ObjectIdQuery;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.dataobjects.auto._PkgVersion;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haikuos.haikudepotserver.support.SingleCollector;
import org.haikuos.haikudepotserver.support.VersionCoordinates;
import org.haikuos.haikudepotserver.support.cayenne.ExpressionHelper;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PkgVersion extends _PkgVersion implements CreateAndModifyTimestamped {

    public final static Pattern MAJOR_PATTERN = Pattern.compile("^[\\w_]+$");
    public final static Pattern MINOR_PATTERN = Pattern.compile("^[\\w_]+$");
    public final static Pattern MICRO_PATTERN = Pattern.compile("^[\\w_.]+$");
    public final static Pattern PRE_RELEASE_PATTERN = Pattern.compile("^[\\w_.]+$");

    public static PkgVersion get(ObjectContext context, ObjectId objectId) {
        return ((List<PkgVersion>) context.performQuery(new ObjectIdQuery(objectId))).stream().collect(SingleCollector.single());
    }

    public static List<PkgVersion> getForPkg(
            ObjectContext context,
            Pkg pkg,
            boolean includeInactive) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);
        return getForPkg(context, pkg, null, null, includeInactive);
    }

    public static List<PkgVersion> getForPkg(
            ObjectContext context,
            Pkg pkg,
            Repository repository,
            boolean includeInactive) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);
        Preconditions.checkArgument(null!=repository, "a repository must be supplied to give context to obtaining a pkg version for a pkg");
        return getForPkg(context, pkg, null, repository, includeInactive);
    }

    public static List<PkgVersion> getForPkg(
            ObjectContext context,
            Pkg pkg,
            RepositorySource repositorySource,
            boolean includeInactive) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);
        Preconditions.checkArgument(null!=repositorySource, "a repository source must be supplied to give context to obtaining a pkg version for a pkg");
        return getForPkg(context, pkg, repositorySource, null, includeInactive);
    }

    private static List<PkgVersion> getForPkg(
            ObjectContext context,
            Pkg pkg,
            RepositorySource repositorySource,
            Repository repository,
            boolean includeInactive) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);

        List<Expression> expressions = new ArrayList<>();

        expressions.add(ExpressionFactory.matchExp(PkgVersion.PKG_PROPERTY, pkg));

        if(!includeInactive) {
            expressions.add(ExpressionFactory.matchExp(PkgVersion.ACTIVE_PROPERTY, Boolean.TRUE));
        }

        if (null != repositorySource) {
            expressions.add(ExpressionFactory.matchExp(
                    PkgVersion.REPOSITORY_SOURCE_PROPERTY,
                    repositorySource));
        }

        if (null != repository) {
            expressions.add(ExpressionFactory.matchExp(
                    PkgVersion.REPOSITORY_SOURCE_PROPERTY + "." + RepositorySource.REPOSITORY_PROPERTY,
                    repository));
        }

        SelectQuery query = new SelectQuery(PkgVersion.class, ExpressionHelper.andAll(expressions));

        //noinspection unchecked
        return (List<PkgVersion>) context.performQuery(query);
    }

    public static Optional<PkgVersion> getForPkg(
            ObjectContext context,
            Pkg pkg,
            Repository repository,
            Architecture architecture,
            VersionCoordinates versionCoordinates) {

        Preconditions.checkArgument(null!=context);
        Preconditions.checkArgument(null!=pkg);
        Preconditions.checkArgument(null!=architecture);
        Preconditions.checkArgument(null!=versionCoordinates && null!=versionCoordinates.getMajor(), "missing or malformed version coordinates");
        Preconditions.checkArgument(null!=repository, "the repository is required to lookup a package version");

        List<Expression> expressions = new ArrayList<>();

        expressions.add(ExpressionHelper.toExpression(versionCoordinates));
        expressions.add(ExpressionFactory.matchExp(PkgVersion.PKG_PROPERTY, pkg));
        expressions.add(ExpressionFactory.matchExp(PkgVersion.ARCHITECTURE_PROPERTY, architecture));
        expressions.add(ExpressionFactory.matchExp(
                PkgVersion.REPOSITORY_SOURCE_PROPERTY + "." + RepositorySource.REPOSITORY_PROPERTY,
                repository));

        SelectQuery query = new SelectQuery(PkgVersion.class, ExpressionHelper.andAll(expressions));
        return ((List<PkgVersion>) context.performQuery(query)).stream().collect(SingleCollector.optional());
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
                validationResult.addFailure(new BeanValidationFailure(this, MAJOR_PROPERTY, "malformed"));
            }
        }

        if (null != getMinor()) {
            if (!MINOR_PATTERN.matcher(getMinor()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, MINOR_PROPERTY, "malformed"));
            }
        }

        if (null != getMicro()) {
            if (!MICRO_PATTERN.matcher(getMicro()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, MICRO_PROPERTY, "malformed"));
            }
        }

        if (null != getPreRelease()) {
            if (!PRE_RELEASE_PATTERN.matcher(getPreRelease()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, PRE_RELEASE_PROPERTY, "malformed"));
            }
        }

        if (null != getRevision()) {
            if (getRevision() <= 0) {
                validationResult.addFailure(new BeanValidationFailure(this, REVISION_PROPERTY, "lessThanEqualZero"));
            }
        }

        if (getViewCounter() < 0) {
            validationResult.addFailure(new BeanValidationFailure(this, VIEW_COUNTER_PROPERTY, "min"));
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

    public PkgVersionLocalization getPkgVersionLocalizationOrFallback(final NaturalLanguage naturalLanguage) {
        return getPkgVersionLocalizationOrFallbackByCode(naturalLanguage.getCode());
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

    /**
     * <p>This method will provide a URL to the actual data of the package.</p>
     *
     * @return
     */

    public URL getHpkgURL() {
        URL packagesBaseUrl = getRepositorySource().getPackagesBaseURL();

        try {
            return new URL(
                    packagesBaseUrl.getProtocol(),
                    packagesBaseUrl.getHost(),
                    packagesBaseUrl.getPort(),
                    packagesBaseUrl.getPath() + "/" + getHpkgFilename());
        } catch (MalformedURLException mue) {
            throw new IllegalStateException("unable to create the URL to the hpkg data", mue);
        }
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
        toVersionCoordinates().appendPathSegments(builder);
        getArchitecture().appendPathSegments(builder);
        return builder;
    }

    public String toStringWithPkgAndArchitecture() {
        return getPkg().getName() + " - " + toString() + " - " + getArchitecture().getCode();
    }

    @Override
    public String toString() {
        return toVersionCoordinates().toString();
    }

}
