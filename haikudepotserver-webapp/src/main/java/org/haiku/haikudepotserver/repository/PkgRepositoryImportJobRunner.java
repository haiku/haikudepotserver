/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.access.Transaction;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.repository.model.RepositoryImportException;
import org.haiku.pkg.HpkrFileExtractor;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.pkg.PkgOrchestrationService;
import org.haiku.haikudepotserver.repository.model.PkgRepositoryImportJobSpecification;
import org.haiku.haikudepotserver.job.JobOrchestrationService;
import org.haiku.pkg.PkgIterator;
import org.haiku.pkg.model.Pkg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Set;

/**
 * <p>This object is responsible for migrating a HPKR file from a remote repository into the Haiku Depot Server
 * database.  It will copy the data into a local file and then work through it there.</p>
 *
 * <p>The system works by the caller lodging a request to update from a remote repository.  The request may be
 * later superseded by another request for the same repository.  When the import process has capacity then it
 * will undertake the import process.</p>
 */

public class PkgRepositoryImportJobRunner extends AbstractJobRunner<PkgRepositoryImportJobSpecification> {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgRepositoryImportJobRunner.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private PkgOrchestrationService pkgService;

    @Value("${repository.import.populatepayloadlength:false}")
    private boolean shouldPopulatePayloadLength;

    @Override
    public void run(JobOrchestrationService jobOrchestrationService, PkgRepositoryImportJobSpecification specification) {

        Preconditions.checkNotNull(specification);

        ObjectContext mainContext = serverRuntime.getContext();
        Repository repository = Repository.getByCode(mainContext, specification.getRepositoryCode()).get();
        List<RepositorySource> repositorySources = RepositorySource.findActiveByRepository(mainContext, repository);

        if(repositorySources.isEmpty()) {
            LOGGER.warn("did not import for repository {} as there are no sources", specification.getRepositoryCode());
        }
        else {
            if(null!=specification.getRepositorySourceCodes() && specification.getRepositorySourceCodes().isEmpty()) {
                LOGGER.warn("specification contains an empty set of repository source codes");
            }
            else {

                // start a cayenne long-running txn
                Transaction transaction = serverRuntime.getDataDomain().createTransaction();
                Transaction.bindThreadTransaction(transaction);

                try {
                    for (RepositorySource repositorySource : repositorySources) {
                        if(
                                null==specification.getRepositorySourceCodes() ||
                                        specification.getRepositorySourceCodes().contains(repositorySource.getCode())) {
                            runForRepositorySource(mainContext, repositorySource);
                        }
                        else {
                            LOGGER.info("skipping repository source; {}", repositorySource.getCode());
                        }
                    }

                    transaction.commit();
                } catch (Throwable th) {
                    transaction.setRollbackOnly();
                    LOGGER.error("a problem has arisen processing a repository file for repository " + repository.getCode(), th);
                } finally {
                    Transaction.bindThreadTransaction(null);

                    if (Transaction.STATUS_MARKED_ROLLEDBACK == transaction.getStatus()) {
                        try {
                            transaction.rollback();
                        } catch (Exception e) {
                            // ignore
                        }
                    }

                }
            }
        }

    }

    private void runForRepositorySource(
            ObjectContext mainContext,
            RepositorySource repositorySource)
            throws RepositoryImportException {
        URL url;

        try {
            url = new URL(repositorySource.getUrl());
        } catch (MalformedURLException mue) {
            throw new IllegalStateException(
                    "the repository source " + repositorySource + " for repository " +
                            repositorySource.getRepository().getCode() + " has a malformed url; " + repositorySource.getUrl(),
                    mue);
        }

        // now shift the URL's data into a temporary file and then process it.
        File temporaryFile = null;

        try {
            temporaryFile = File.createTempFile(repositorySource.getCode() + "__import", ".hpkr");
            Resources.asByteSource(url).copyTo(Files.asByteSink(temporaryFile));

            LOGGER.debug("did copy data for repository source {} ({}) to temporary file", repositorySource, url.toString());

            HpkrFileExtractor fileExtractor = new HpkrFileExtractor(temporaryFile);

            long startTimeMs = System.currentTimeMillis();
            LOGGER.info("will process data for repository source {}", repositorySource.getCode());

            // import any packages that are in the repository.

            Set<String> repositoryImportPkgNames = Sets.newHashSet();
            PkgIterator pkgIterator = new PkgIterator(fileExtractor.getPackageAttributesIterator());

            while (pkgIterator.hasNext()) {

                ObjectContext pkgImportContext = serverRuntime.getContext();

                Pkg pkg = pkgIterator.next();
                repositoryImportPkgNames.add(pkg.getName());

                pkgService.importFrom(
                        pkgImportContext,
                        repositorySource.getObjectId(),
                        pkg,
                        shouldPopulatePayloadLength);

                try {
                    pkgImportContext.commitChanges();
                }
                catch(Throwable th) {
                    throw new RepositoryImportException("unable to store package [" + pkg.toString() + "]", th);
                }
            }

            // [apl 6.aug.2014] #5
            // Packages may be removed from a repository.  In this case there is no trigger to indicate that the
            // package version should be removed.  Check all of the packages that have an active version in this
            // repository and then if the package simply doesn't exist in that repository any more, mark all of
            // those versions are inactive.

            for (String persistedPkgName : pkgService.fetchPkgNamesWithAnyPkgVersionAssociatedWithRepositorySource(
                    mainContext,
                    repositorySource)) {
                if (!repositoryImportPkgNames.contains(persistedPkgName)) {

                    ObjectContext removalContext = serverRuntime.getContext();
                    RepositorySource removalRepositorySource = RepositorySource.get(removalContext, repositorySource.getObjectId());

                    int changes = pkgService.deactivatePkgVersionsForPkgAssociatedWithRepositorySource(
                            removalContext,
                            org.haiku.haikudepotserver.dataobjects.Pkg.getByName(removalContext, persistedPkgName).get(),
                            removalRepositorySource);

                    if (changes > 0) {
                        removalContext.commitChanges();
                        LOGGER.info("did remove all versions of package {} from repository source {} because this package is no longer in the repository", persistedPkgName, repositorySource.toString());
                    }

                }
            }

            LOGGER.info("did process data for repository source {} in {}ms", repositorySource, System.currentTimeMillis() - startTimeMs);

        }
        catch (Throwable th) {
            throw new RuntimeException("a problem has arisen processing a repository file for repository source " + repositorySource + " from url '" + url.toString() + "'", th);
        }
        finally {

            if (null != temporaryFile && temporaryFile.exists()) {
                if (!temporaryFile.delete()) {
                    LOGGER.error("unable to delete the file; {}" + temporaryFile.getAbsolutePath());
                }
            }

        }
    }

}
