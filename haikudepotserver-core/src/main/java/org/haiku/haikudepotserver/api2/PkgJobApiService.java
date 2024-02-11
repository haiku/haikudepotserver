/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api2.model.*;
import org.haiku.haikudepotserver.pkg.model.*;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.auto._User;
import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobData;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.security.PermissionEvaluator;
import org.haiku.haikudepotserver.security.model.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

@Component("pkgJobApiServiceV2")
public class PkgJobApiService extends AbstractApiService {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgJobApiService.class);

    private final ServerRuntime serverRuntime;
    private final PermissionEvaluator permissionEvaluator;
    private final JobService jobService;

    public PkgJobApiService(
            ServerRuntime serverRuntime,
            PermissionEvaluator permissionEvaluator,
            JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    public QueuePkgCategoryCoverageExportSpreadsheetJobResult queuePkgCategoryCoverageExportSpreadsheetJob() {
        return new QueuePkgCategoryCoverageExportSpreadsheetJobResult()
                .guid(queueSimplePkgJob(
                        PkgCategoryCoverageExportSpreadsheetJobSpecification.class,
                        Permission.BULK_PKGCATEGORYCOVERAGEEXPORTSPREADSHEET));
    }

    public QueuePkgCategoryCoverageImportSpreadsheetJobResult queuePkgCategoryCoverageImportSpreadsheetJob(
            QueuePkgCategoryCoverageImportSpreadsheetJobRequestEnvelope request) {
        Preconditions.checkArgument(null != request, "the request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getInputDataGuid()), "the input data must be identified by guid");

        final ObjectContext context = serverRuntime.newContext();

        Optional<User> user = tryObtainAuthenticatedUser(context);

        if(!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                Permission.BULK_PKGCATEGORYCOVERAGEIMPORTSPREADSHEET)) {
            throw new AccessDeniedException("attempt to import package categories, but was not authorized");
        }

        // now check that the data is present.

        jobService.tryGetData(request.getInputDataGuid())
                .orElseThrow(() -> new ObjectNotFoundException(JobData.class.getSimpleName(), request.getInputDataGuid()));

        // setup and go

        PkgCategoryCoverageImportSpreadsheetJobSpecification spec = new PkgCategoryCoverageImportSpreadsheetJobSpecification();
        spec.setOwnerUserNickname(user.map(_User::getNickname).orElse(null));
        spec.setInputDataGuid(request.getInputDataGuid());

        return new QueuePkgCategoryCoverageImportSpreadsheetJobResult()
                .guid(jobService.submit(spec, JobSnapshot.COALESCE_STATUSES_NONE));
    }

    public QueuePkgIconArchiveImportJobResult queuePkgIconArchiveImportJob(
            QueuePkgIconArchiveImportJobRequestEnvelope request) {
        Preconditions.checkArgument(null != request, "the request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getInputDataGuid()), "the input data must be identified by guid");

        final ObjectContext context = serverRuntime.newContext();

        Optional<User> user = tryObtainAuthenticatedUser(context);

        if(!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                Permission.BULK_PKGICONIMPORTARCHIVE)) {
            throw new AccessDeniedException("attempt to import package icons, but was not authorized");
        }

        // now check that the data is present.

        jobService.tryGetData(request.getInputDataGuid())
                .orElseThrow(() -> new ObjectNotFoundException(JobData.class.getSimpleName(), request.getInputDataGuid()));

        // setup and go

        PkgIconImportArchiveJobSpecification spec = new PkgIconImportArchiveJobSpecification();
        spec.setOwnerUserNickname(user.map(_User::getNickname).orElse(null));
        spec.setInputDataGuid(request.getInputDataGuid());

        return new QueuePkgIconArchiveImportJobResult()
                .guid(jobService.submit(spec, JobSnapshot.COALESCE_STATUSES_NONE));
    }

    public QueuePkgIconExportArchiveJobResult queuePkgIconExportArchiveJob() {
        return new QueuePkgIconExportArchiveJobResult()
                .guid(queueSimplePkgJob(
                        PkgIconExportArchiveJobSpecification.class,
                        Permission.BULK_PKGICONEXPORTARCHIVE));
    }

    public QueuePkgIconSpreadsheetJobResult queuePkgIconSpreadsheetJob() {
        return new QueuePkgIconSpreadsheetJobResult()
                .guid(queueSimplePkgJob(
                        PkgIconSpreadsheetJobSpecification.class,
                        Permission.BULK_PKGICONSPREADSHEETREPORT));
    }

    public QueuePkgLocalizationCoverageExportSpreadsheetJobResult queuePkgLocalizationCoverageExportSpreadsheetJob() {
        return new QueuePkgLocalizationCoverageExportSpreadsheetJobResult()
                .guid(queueSimplePkgJob(
                        PkgLocalizationCoverageExportSpreadsheetJobSpecification.class,
                        Permission.BULK_PKGLOCALIZATIONCOVERAGEEXPORTSPREADSHEET));
    }

    public QueuePkgProminenceAndUserRatingSpreadsheetJobResult queuePkgProminenceAndUserRatingSpreadsheetJob() {
        return new QueuePkgProminenceAndUserRatingSpreadsheetJobResult()
                .guid(queueSimplePkgJob(
                        PkgProminenceAndUserRatingSpreadsheetJobSpecification.class,
                        Permission.BULK_PKGPROMINENCEANDUSERRATINGSPREADSHEETREPORT));
    }

    public QueuePkgScreenshotArchiveImportJobResult queuePkgScreenshotArchiveImportJob(
            QueuePkgScreenshotArchiveImportJobRequestEnvelope request) {
        Preconditions.checkArgument(null != request, "the request must be supplied");
        Preconditions.checkArgument(StringUtils.isNotBlank(request.getInputDataGuid()), "the data guid must be supplied");
        Preconditions.checkArgument(null != request.getImportStrategy(), "the import strategy must be supplied");

        final ObjectContext context = serverRuntime.newContext();

        Optional<User> user = tryObtainAuthenticatedUser(context);

        if(!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                Permission.BULK_PKGSCREENSHOTIMPORTARCHIVE)) {
            throw new AccessDeniedException("attempt to import package screenshots, but was not authorized");
        }

        // now check that the data is present.

        jobService.tryGetData(request.getInputDataGuid())
                .orElseThrow(() -> new ObjectNotFoundException(JobData.class.getSimpleName(), request.getInputDataGuid()));

        // setup and go

        PkgScreenshotImportArchiveJobSpecification spec = new PkgScreenshotImportArchiveJobSpecification();
        spec.setOwnerUserNickname(user.map(_User::getNickname).orElse(null));
        spec.setInputDataGuid(request.getInputDataGuid());
        spec.setImportStrategy(PkgScreenshotImportArchiveJobSpecification.ImportStrategy.valueOf(request.getImportStrategy().name()));

        return new QueuePkgScreenshotArchiveImportJobResult()
                .guid(jobService.submit(spec, JobSnapshot.COALESCE_STATUSES_NONE));
    }

    public QueuePkgScreenshotExportArchiveJobResult queuePkgScreenshotExportArchiveJob(
            QueuePkgScreenshotExportArchiveJobRequestEnvelope request) {
        Preconditions.checkArgument(null != request, "the request must be supplied");

        final ObjectContext context = serverRuntime.newContext();
        User user = obtainAuthenticatedUser(context);

        if(!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                Permission.BULK_PKGSCREENSHOTEXPORTARCHIVE)) {
            throw new AccessDeniedException("attempt to export pkg screenshots as an archive, but was not authorized");
        }

        PkgScreenshotExportArchiveJobSpecification specification = new PkgScreenshotExportArchiveJobSpecification();
        specification.setOwnerUserNickname(user.getNickname());
        specification.setPkgName(request.getPkgName());

        return new QueuePkgScreenshotExportArchiveJobResult()
                .guid(jobService.submit(specification, JobSnapshot.COALESCE_STATUSES_NONE));
    }

    public QueuePkgScreenshotSpreadsheetJobResult queuePkgScreenshotSpreadsheetJob() {
        return new QueuePkgScreenshotSpreadsheetJobResult()
                .guid(queueSimplePkgJob(
                        PkgScreenshotSpreadsheetJobSpecification.class,
                        Permission.BULK_PKGICONSPREADSHEETREPORT));
    }

    public QueuePkgVersionLocalizationCoverageExportSpreadsheetJobResult queuePkgVersionLocalizationCoverageExportSpreadsheetJob() {
        return new QueuePkgVersionLocalizationCoverageExportSpreadsheetJobResult()
                .guid(queueSimplePkgJob(
                        PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification.class,
                        Permission.BULK_PKGVERSIONLOCALIZATIONCOVERAGEEXPORTSPREADSHEET));
    }

    public QueuePkgVersionPayloadLengthPopulationJobResult queuePkgVersionPayloadLengthPopulationJob() {
        return new QueuePkgVersionPayloadLengthPopulationJobResult()
                .guid(queueSimplePkgJob(
                        PkgVersionPayloadLengthPopulationJobSpecification.class,
                        Permission.BULK_PKGVERSIONPAYLOADLENGTHPOPULATION));
    }

    public QueuePkgDumpExportJobResult queuePkgDumpExportJob(QueuePkgDumpExportJobRequestEnvelope request) {

        final ObjectContext context = serverRuntime.newContext();
        User user = obtainAuthenticatedUser(context);

        PkgDumpExportJobSpecification specification = new PkgDumpExportJobSpecification();
        specification.setOwnerUserNickname(user.getNickname());
        specification.setNaturalLanguageCode(
                Optional.ofNullable(request.getNaturalLanguageCode())
                        .orElseGet(() -> user.getNaturalLanguage().getCode()));
        specification.setRepositorySourceCode(request.getRepositorySourceCode());

        return new QueuePkgDumpExportJobResult()
                .guid(jobService.submit(specification, JobSnapshot.COALESCE_STATUSES_NONE));
    }

    public QueuePkgDumpLocalizationExportJobResult queuePkgDumpLocalizationExportJob() {
        final ObjectContext context = serverRuntime.newContext();
        User user = obtainAuthenticatedUser(context);
        PkgDumpLocalizationExportJobSpecification specification = new PkgDumpLocalizationExportJobSpecification();
        specification.setOwnerUserNickname(user.getNickname());
        return new QueuePkgDumpLocalizationExportJobResult()
                .guid(jobService.submit(specification, JobSnapshot.COALESCE_STATUSES_NONE));
    }

    private String queueSimplePkgJob(
            Class<? extends AbstractJobSpecification> jobSpecificationClass,
            Permission permission) {

        final ObjectContext context = serverRuntime.newContext();

        User user = obtainAuthenticatedUser(context);

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                permission)) {
            throw new AccessDeniedException("attempt to queue [" + jobSpecificationClass.getSimpleName() + "] without sufficient authorization");
        }

        AbstractJobSpecification spec;

        try {
            spec = jobSpecificationClass.getDeclaredConstructor().newInstance();
        }
        catch(InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("unable to create the job specification for class; " + jobSpecificationClass.getSimpleName(), e);
        }

        spec.setOwnerUserNickname(user.getNickname());

        return jobService.submit(spec, JobSnapshot.COALESCE_STATUSES_QUEUED_STARTED);
    }

}
