/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository.job;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.driversettings.DriverSettings;
import org.haiku.driversettings.DriverSettingsException;
import org.haiku.driversettings.Parameter;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgImportService;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.repository.model.RepositoryHpkrIngressException;
import org.haiku.haikudepotserver.repository.model.RepositoryHpkrIngressJobSpecification;
import org.haiku.haikudepotserver.support.FileHelper;
import org.haiku.pkg.HpkrFileExtractor;
import org.haiku.pkg.PkgIterator;
import org.haiku.pkg.model.Pkg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * <p>This object is responsible for migrating a HPKR file from a remote repository into the Haiku Depot Server
 * database.  It will copy the data into a local file and then work through it there.</p>
 *
 * <p>The system works by the caller lodging a request to update from a remote repository.  The request may be
 * later superseded by another request for the same repository.  When the import process has capacity then it
 * will undertake the import process.</p>
 */

@Component
public class RepositoryHpkrIngressJobRunner extends AbstractJobRunner<RepositoryHpkrIngressJobSpecification> {

    protected static Logger LOGGER = LoggerFactory.getLogger(RepositoryHpkrIngressJobRunner.class);

    private final static long TIMEOUT_REPOSITORY_SOURCE_FETCH = TimeUnit.SECONDS.toMillis(30);

    private final static String PARAMETER_NAME_IDENTIFIER = "identifier";
    private final static String PARAMETER_ARCHITECTURE = "architecture";

    private final ServerRuntime serverRuntime;
    private final PkgService pkgService;
    private final PkgImportService pkgImportService;
    private final boolean shouldPopulateFromPayload;
    private final Pattern allowedPkgNamePattern;

    public RepositoryHpkrIngressJobRunner(
            ServerRuntime serverRuntime,
            PkgService pkgService,
            PkgImportService pkgImportService,
            @Value("${hds.repository.import.populate-from-payload:false}") boolean shouldPopulateFromPayload,
            @Value("${hds.repository.import.allowed-pkg-name-pattern:}") String allowedPkgNamePattern) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.pkgService = Preconditions.checkNotNull(pkgService);
        this.pkgImportService = Preconditions.checkNotNull(pkgImportService);
        this.shouldPopulateFromPayload = shouldPopulateFromPayload;
        this.allowedPkgNamePattern = Optional.ofNullable(allowedPkgNamePattern)
                .filter(StringUtils::isNotEmpty)
                .map(Pattern::compile)
                .orElse(null);
    }

    @Override
    public Class<RepositoryHpkrIngressJobSpecification> getSupportedSpecificationClass() {
        return RepositoryHpkrIngressJobSpecification.class;
    }

    @Override
    public void run(JobService jobService, RepositoryHpkrIngressJobSpecification specification) {

        Preconditions.checkNotNull(specification);

        ObjectContext mainContext = serverRuntime.newContext();
        Set<String> allowedRepositorySourceCodes = specification.getRepositorySourceCodes();

        RepositorySource.findActiveByRepository(
                mainContext,
                Repository.getByCode(mainContext, specification.getRepositoryCode()))
                .stream()
                .filter(rs -> null == allowedRepositorySourceCodes || allowedRepositorySourceCodes.contains(rs.getCode()))
                .forEach(rs ->
                        serverRuntime.performInTransaction(() -> {
                            try {
                                runForRepositorySource(mainContext, rs);
                            } catch (Throwable e) {
                                LOGGER.error(
                                        "a problem has arisen processing a repository file for repository source [{}]",
                                        rs.getCode(), e);
                            }

                            return null;
                        })
                );
    }

    private void runForRepositorySource(
            ObjectContext mainContext,
            RepositorySource repositorySource)
            throws RepositoryHpkrIngressException {
        LOGGER.info("will import for repository source [{}]", repositorySource);

        runImportInfoForRepositorySource(mainContext, repositorySource);
        runImportHpkrForRepositorySource(mainContext, repositorySource);

        repositorySource.setLastImportTimestamp();
        mainContext.commitChanges();
    }

    /**
     * <p>Each repository has a little &quot;repo.info&quot; file that resides next to the HPKR data.
     * This method will pull this in and process the data into the repository source.</p>
     */

    private void runImportInfoForRepositorySource(
            ObjectContext mainContext,
            RepositorySource repositorySource)
    throws RepositoryHpkrIngressException {
        URI uri = repositorySource.tryGetInternalFacingDownloadRepoInfoURI().orElseThrow(
                () -> new RepositoryHpkrIngressException(
                        "unable to download for [" + repositorySource.getCode()
                        + "] as no download repo info url was available"));

        // now shift the URL's data into a temporary file and then process it.
        File temporaryFile = null;

        try {
            temporaryFile = File.createTempFile(repositorySource.getCode() + "__import", ".repo-info");

            LOGGER.info("will copy data for repository info [{}] ({}) to temporary file",
                    repositorySource, uri.toString());

            FileHelper.streamUrlDataToFile(uri, temporaryFile, TIMEOUT_REPOSITORY_SOURCE_FETCH);

            try (
                    InputStream inputStream = new FileInputStream(temporaryFile);
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                    BufferedReader reader = new BufferedReader(inputStreamReader)
                    ) {
                List<Parameter> parameters = DriverSettings.parse(reader);
                String identifierParameterValue = tryGetParameterValue(parameters, PARAMETER_NAME_IDENTIFIER)
                        .orElseThrow(() -> new DriverSettingsException(
                                "expected to find the parameter [" + PARAMETER_NAME_IDENTIFIER + "]"));

                if (!Objects.equals(identifierParameterValue, repositorySource.getIdentifier())) {
                    LOGGER.info("updated the repo info identifier to [{}] for repository source [{}]",
                            identifierParameterValue, repositorySource.getCode());
                    repositorySource.setIdentifier(identifierParameterValue);
                    repositorySource.getRepository().setModifyTimestamp();
                    mainContext.commitChanges();
                }

                Optional<String> architectureCodeOptional = tryGetParameterValue(parameters, PARAMETER_ARCHITECTURE);

                if (architectureCodeOptional.isEmpty()) {
                    throw new RepositoryHpkrIngressException(
                            "repository source [" + repositorySource.getCode()
                                    + "] has no architecture code");
                }

                Optional<Architecture> architectureOptional = Architecture.tryGetByCode(mainContext, architectureCodeOptional.get());

                if (architectureOptional.isEmpty()) {
                    throw new RepositoryHpkrIngressException(
                            "repository source [" + repositorySource.getCode()
                                    + "] has unknown architecture code ["
                                    + architectureCodeOptional.get() + "]");
                }

                repositorySource.setArchitecture(architectureOptional.get());
            }
        } catch (IOException | DriverSettingsException e) {
            throw new RepositoryHpkrIngressException(
                    "a problem has arisen parsing or dealing with the 'repo.info' file", e);
        } finally {
            if (null != temporaryFile && temporaryFile.exists()) {
                if (!temporaryFile.delete()) {
                    LOGGER.error("unable to delete the file; {}", temporaryFile.getAbsolutePath());
                }
            }
        }
    }

    private Optional<String> tryGetParameterValue(List<Parameter> parameters, String parameterName) {
        return tryGetParameter(parameters, parameterName)
                .map(Parameter::getValues)
                .filter(org.apache.commons.collections4.CollectionUtils::isNotEmpty).flatMap(vs -> vs.stream()
                        .filter(StringUtils::isNotBlank)
                        .findFirst());
    }

    private Optional<Parameter> tryGetParameter(List<Parameter> parameters, String parameterName) {
        return org.apache.commons.collections4.CollectionUtils.emptyIfNull(parameters)
                .stream()
                .filter(p -> p.getName().equals(parameterName))
                .findFirst();
    }

    private void runImportHpkrForRepositorySource(
            ObjectContext mainContext,
            RepositorySource repositorySource) {
        URI uri = repositorySource.tryGetInternalFacingDownloadHpkrURI()
                .orElseThrow(() -> new RuntimeException(
                        "unable to import for ["
                                + repositorySource
                                + "] as there is no download url able to be derived."));

        // now shift the URL's data into a temporary file and then process it.
        File temporaryFile = null;

        try {
            temporaryFile = File.createTempFile(repositorySource.getCode() + "__import", ".hpkr");

            LOGGER.info("will copy repository hpkr [{}] ({}) to temporary file",
                    repositorySource, uri.toString());

            FileHelper.streamUrlDataToFile(uri, temporaryFile, TIMEOUT_REPOSITORY_SOURCE_FETCH);

            LOGGER.info("did copy {} bytes for repository hpkr [{}] ({}) to temporary file",
                    temporaryFile.length(), repositorySource, uri);

            Set<String> repositoryImportPkgNames = Sets.newHashSet();
            long startTimeMs = System.currentTimeMillis();

            try (HpkrFileExtractor fileExtractor = new HpkrFileExtractor(temporaryFile)) {

                LOGGER.info("will process data for repository hpkr {}", repositorySource.getCode());

                // import any packages that are in the repository.

                PkgIterator pkgIterator = new PkgIterator(fileExtractor.getPackageAttributesIterator());

                while (pkgIterator.hasNext()) {
                    Pkg pkg = pkgIterator.next();

                    repositoryImportPkgNames.add(pkg.getName());

                    if (null == allowedPkgNamePattern || allowedPkgNamePattern.matcher(pkg.getName()).matches()) {
                        ObjectContext pkgImportContext = serverRuntime.newContext();

                        try {
                            pkgImportService.importFrom(
                                    pkgImportContext,
                                    repositorySource.getObjectId(),
                                    pkg,
                                    shouldPopulateFromPayload);

                            pkgImportContext.commitChanges();
                        } catch (Throwable th) {
                            throw new RepositoryHpkrIngressException("unable to store package [" + pkg + "]", th);
                        }
                    } else {
                        LOGGER.info("skipping pkg [{}] because it is not in the allowed pkg name pattern", pkg.getName());
                    }
                }
            }

            // [apl 6.aug.2014] #5
            // Packages may be removed from a repository.  In this case there is no trigger to indicate that the
            // package version should be removed.  Check all the packages that have an active version in this
            // repository and then if the package simply doesn't exist in that repository anymore, mark all of
            // those versions are inactive.

            pkgService.fetchPkgNamesWithAnyPkgVersionAssociatedWithRepositorySource(
                    mainContext,
                    repositorySource).forEach((persistedPkgName) -> {
                if (!repositoryImportPkgNames.contains(persistedPkgName)) {

                    ObjectContext removalContext = serverRuntime.newContext();
                    RepositorySource removalRepositorySource = RepositorySource.get(
                            removalContext,
                            repositorySource.getObjectId());

                    int changes = pkgService.deactivatePkgVersionsForPkgAssociatedWithRepositorySource(
                            removalContext,
                            org.haiku.haikudepotserver.dataobjects.Pkg.getByName(removalContext, persistedPkgName),
                            removalRepositorySource);

                    if (changes > 0) {
                        removalContext.commitChanges();
                        LOGGER.info("did remove all versions of package {} from repository source {} because this "
                            + "package is no longer in the repository", persistedPkgName, repositorySource);
                    }

                }
            });

            LOGGER.info("did process data for repository hpkr {} in {}ms", repositorySource,
                    System.currentTimeMillis() - startTimeMs);

        } catch (Throwable th) {
            throw new RuntimeException("a problem has arisen processing a repository file for repository hpkr "
                    + repositorySource + " from url '" + uri.toString() + "'", th);
        } finally {

            if (null != temporaryFile && temporaryFile.exists()) {
                if (!temporaryFile.delete()) {
                    LOGGER.error("unable to delete the file; {}", temporaryFile.getAbsolutePath());
                }
            }

        }
    }

}
