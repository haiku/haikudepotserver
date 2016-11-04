/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api1.model.AbstractQueueJobResult;
import org.haiku.haikudepotserver.api1.model.pkg.job.*;
import org.haiku.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobData;
import org.haiku.haikudepotserver.pkg.model.*;
import org.haiku.haikudepotserver.security.AuthorizationService;
import org.haiku.haikudepotserver.security.model.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.util.Optional;

@AutoJsonRpcServiceImpl
public class PkgJobApiImpl extends AbstractApiImpl implements PkgJobApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgJobApiImpl.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private AuthorizationService authorizationService;

    @Resource
    private JobService jobService;

    @Override
    public QueuePkgCategoryCoverageExportSpreadsheetJobResult queuePkgCategoryCoverageExportSpreadsheetJob(QueuePkgCategoryCoverageExportSpreadsheetJobRequest request) {
        Preconditions.checkArgument(null != request);
        return queueSimplePkgJob(
                QueuePkgCategoryCoverageExportSpreadsheetJobResult.class,
                PkgCategoryCoverageExportSpreadsheetJobSpecification.class,
                Permission.BULK_PKGCATEGORYCOVERAGEEXPORTSPREADSHEET);
    }

    @Override
    public QueuePkgCategoryCoverageImportSpreadsheetJobResult queuePkgCategoryCoverageImportSpreadsheetJob(
            QueuePkgCategoryCoverageImportSpreadsheetJobRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null != request, "the request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.inputDataGuid), "the input data must be identified by guid");

        final ObjectContext context = serverRuntime.getContext();

        Optional<User> user = tryObtainAuthenticatedUser(context);

        if(!authorizationService.check(
                context,
                user.orElse(null),
                null,
                Permission.BULK_PKGCATEGORYCOVERAGEIMPORTSPREADSHEET)) {
            LOGGER.warn("attempt to import package categories, but was not authorized");
            throw new AuthorizationFailureException();
        }

        // now check that the data is present.

        jobService.tryGetData(request.inputDataGuid)
                .orElseThrow(() -> new ObjectNotFoundException(JobData.class.getSimpleName(), request.inputDataGuid));

        // setup and go

        PkgCategoryCoverageImportSpreadsheetJobSpecification spec = new PkgCategoryCoverageImportSpreadsheetJobSpecification();
        spec.setOwnerUserNickname(user.map((u) -> u.getNickname()).orElse(null));
        spec.setInputDataGuid(request.inputDataGuid);

        return new QueuePkgCategoryCoverageImportSpreadsheetJobResult(
                jobService.submit(spec, JobService.CoalesceMode.NONE).orElse(null));
    }

    @Override
    public QueuePkgIconSpreadsheetJobResult queuePkgIconSpreadsheetJob(QueuePkgIconSpreadsheetJobRequest request) {
        Preconditions.checkArgument(null != request);
        return queueSimplePkgJob(
                QueuePkgIconSpreadsheetJobResult.class,
                PkgIconSpreadsheetJobSpecification.class,
                Permission.BULK_PKGICONSPREADSHEETREPORT);
    }

    @Override
    public QueuePkgProminenceAndUserRatingSpreadsheetJobResult queuePkgProminenceAndUserRatingSpreadsheetJob(QueuePkgProminenceAndUserRatingSpreadsheetJobRequest request) {
        Preconditions.checkArgument(null!=request);
        return queueSimplePkgJob(
                QueuePkgProminenceAndUserRatingSpreadsheetJobResult.class,
                PkgProminenceAndUserRatingSpreadsheetJobSpecification.class,
                Permission.BULK_PKGPROMINENCEANDUSERRATINGSPREADSHEETREPORT);
    }

    @Override
    public QueuePkgIconExportArchiveJobResult queuePkgIconExportArchiveJob(QueuePkgIconExportArchiveJobRequest request) {
        Preconditions.checkArgument(null!=request);
        return queueSimplePkgJob(
                QueuePkgIconExportArchiveJobResult.class,
                PkgIconExportArchiveJobSpecification.class,
                Permission.BULK_PKGICONEXPORTARCHIVE);
    }

    @Override
    public QueuePkgVersionPayloadLengthPopulationJobResult queuePkgVersionPayloadLengthPopulationJob(QueuePkgVersionPayloadLengthPopulationJobRequest request) {
        Preconditions.checkArgument(null!=request, "a request objects is required");
        return queueSimplePkgJob(
                QueuePkgVersionPayloadLengthPopulationJobResult.class,
                PkgVersionPayloadLengthPopulationJobSpecification.class,
                Permission.BULK_PKGVERSIONPAYLOADLENGTHPOPULATION);
    }

    @Override
    public QueuePkgVersionLocalizationCoverageExportSpreadsheetJobResult queuePkgVersionLocalizationCoverageExportSpreadsheetJob(QueuePkgVersionLocalizationCoverageExportSpreadsheetJobRequest request) {
        Preconditions.checkArgument(null!=request, "a request objects is required");
        return queueSimplePkgJob(
                QueuePkgVersionLocalizationCoverageExportSpreadsheetJobResult.class,
                PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification.class,
                Permission.BULK_PKGVERSIONLOCALIZATIONCOVERAGEEXPORTSPREADSHEET);
    }

    @Override
    public QueuePkgLocalizationCoverageExportSpreadsheetJobResult queuePkgLocalizationCoverageExportSpreadsheetJob(QueuePkgLocalizationCoverageExportSpreadsheetJobRequest request) {
        Preconditions.checkArgument(null!=request, "a request objects is required");
        return queueSimplePkgJob(
                QueuePkgLocalizationCoverageExportSpreadsheetJobResult.class,
                PkgLocalizationCoverageExportSpreadsheetJobSpecification.class,
                Permission.BULK_PKGLOCALIZATIONCOVERAGEEXPORTSPREADSHEET);
    }

    private <R extends AbstractQueueJobResult> R queueSimplePkgJob(
            Class<R> resultClass,
            Class<? extends AbstractJobSpecification> jobSpecificationClass,
            Permission permission) {

        final ObjectContext context = serverRuntime.getContext();

        Optional<User> user = tryObtainAuthenticatedUser(context);

        if (!user.isPresent()) {
            LOGGER.warn("attempt to queue {} without a user", jobSpecificationClass.getSimpleName());
            throw new AuthorizationFailureException();
        }

        if (!authorizationService.check(context, user.get(), null, permission)) {
            LOGGER.warn("attempt to queue {} without sufficient authorization", jobSpecificationClass.getSimpleName());
            throw new AuthorizationFailureException();
        }

        AbstractJobSpecification spec;

        try {
            spec = jobSpecificationClass.newInstance();
        }
        catch(InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("unable to create the job specification for class; " + jobSpecificationClass.getSimpleName(), e);
        }

        spec.setOwnerUserNickname(user.get().getNickname());

        R result;

        try {
            result = resultClass.newInstance();
        }
        catch(InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("unable to create the result; " + resultClass.getSimpleName(), e);
        }

        result.guid = jobService.submit(spec, JobService.CoalesceMode.QUEUEDANDSTARTED).orElse(null);
        return result;
    }

}
