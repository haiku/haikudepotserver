/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.repository;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.access.Transaction;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.Repository;
import org.haikuos.haikudepotserver.pkg.PkgOrchestrationService;
import org.haikuos.haikudepotserver.repository.model.PkgRepositoryImportJobSpecification;
import org.haikuos.haikudepotserver.support.job.AbstractJobRunner;
import org.haikuos.pkg.PkgIterator;
import org.haikuos.pkg.model.Pkg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

/**
 * <p>This object is responsible for migrating a HPKR file from a remote repository into the Haiku Depot Server
 * database.  It will copy the data into a local file and then work through it there.</p>
 *
 * <p>The system works by the caller lodging a request to update from a remote repository.  The request may be
 * later superceeded by another request for the same repository.  When the import process has capacity then it
 * will undertake the import process.</p>
 */

public class PkgRepositoryImportJobRunner extends AbstractJobRunner<PkgRepositoryImportJobSpecification> {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgRepositoryImportJobRunner.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    PkgOrchestrationService pkgService;

    public void run(PkgRepositoryImportJobSpecification specification) {

        Preconditions.checkNotNull(specification);

        ObjectContext mainContext = serverRuntime.getContext();
        Repository repository = Repository.getByCode(mainContext, specification.getCode()).get();
        URL url;

        try {
            url = new URL(repository.getUrl());
        }
        catch(MalformedURLException mue) {
            throw new IllegalStateException("the repository "+specification.getCode()+" has a malformed url; "+repository.getUrl(),mue);
        }

        // now shift the URL's data into a temporary file and then process it.
        File temporaryFile = null;

        // start a cayenne long-running txn
        Transaction transaction = serverRuntime.getDataDomain().createTransaction();
        Transaction.bindThreadTransaction(transaction);

        try {
            temporaryFile = File.createTempFile(specification.getCode()+"__import",".hpkr");
            Resources.asByteSource(url).copyTo(Files.asByteSink(temporaryFile));

            LOGGER.debug("did copy data for repository {} ({}) to temporary file", specification.getCode(), url.toString());

            org.haikuos.pkg.HpkrFileExtractor fileExtractor = new org.haikuos.pkg.HpkrFileExtractor(temporaryFile);

            long startTimeMs = System.currentTimeMillis();
            LOGGER.info("will process data for repository {}", specification.getCode());

            // import any packages that are in the repository.

            Set<String> repositoryImportPkgNames = Sets.newHashSet();
            PkgIterator pkgIterator = new PkgIterator(fileExtractor.getPackageAttributesIterator());

            while (pkgIterator.hasNext()) {
                ObjectContext pkgImportContext = serverRuntime.getContext();
                Pkg pkg = pkgIterator.next();
                repositoryImportPkgNames.add(pkg.getName());
                pkgService.importFrom(pkgImportContext, repository.getObjectId(), pkg);
                pkgImportContext.commitChanges();
            }

            // [apl 6.aug.2014] #5
            // Packages may be removed from a repository.  In this case there is no trigger to indicate that the
            // package version should be removed.  Check all of the packages that have an active version in this
            // repository and then if the package simply doesn't exist in that repository any more, mark all of
            // those versions are inactive.

            for (String persistedPkgName : pkgService.fetchPkgNamesWithAnyPkgVersionAssociatedWithRepository(
                    mainContext,
                    repository)) {
                if (!repositoryImportPkgNames.contains(persistedPkgName)) {

                    ObjectContext removalContext = serverRuntime.getContext();
                    Repository removalRepository = Repository.get(removalContext, repository.getObjectId());

                    int changes = pkgService.deactivatePkgVersionsForPkgAssociatedWithRepository(
                            removalContext,
                            org.haikuos.haikudepotserver.dataobjects.Pkg.getByName(removalContext, persistedPkgName).get(),
                            removalRepository);

                    if (changes > 0) {
                        removalContext.commitChanges();
                        LOGGER.info("did remove all versions of package {} from repository {} because this package is no longer in the repository", persistedPkgName, repository.toString());
                    }

                }
            }

            LOGGER.info("did process data for repository {} in {}ms", specification.getCode(), System.currentTimeMillis() - startTimeMs);

            transaction.commit();
        }
        catch(Throwable th) {

            transaction.setRollbackOnly();
            LOGGER.error("a problem has arisen processing a repository file for repository " + specification.getCode() + " from url '" + url.toString() + "'", th);

        }
        finally {

            if(null!=temporaryFile && temporaryFile.exists()) {
                if(!temporaryFile.delete()) {
                    LOGGER.error("unable to delete the file; {}" + temporaryFile.getAbsolutePath());
                }
            }

            Transaction.bindThreadTransaction(null);

            if (Transaction.STATUS_MARKED_ROLLEDBACK == transaction.getStatus()) {
                try {
                    transaction.rollback();
                }
                catch(Exception e) {
                    // ignore
                }
            }

        }

    }

}
