/*
 * Copyright 2016-2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haiku.haikudepotserver.api1.model.pkg.job.*;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;

@JsonRpcService("/__api/v1/pkg/job")
public interface PkgJobApi {

    /**
     * <p>Enqueues a request on behalf of the current user to produce a spreadsheet showing the coverage of categories
     * for the packages.  See the {@link JobApi} for details on how to control the
     * job.</p>
     */

    QueuePkgCategoryCoverageExportSpreadsheetJobResult queuePkgCategoryCoverageExportSpreadsheetJob(QueuePkgCategoryCoverageExportSpreadsheetJobRequest request);

    /**
     * <P>Enqueues a request on behalf od the current user to import package data from a spreadsheet that is uploaded
     * to the server.  It does this and also produces an outbound spreadsheet of the result.</P>
     * @throws ObjectNotFoundException in the case that the data identified by GUID does not exist.
     */

    QueuePkgCategoryCoverageImportSpreadsheetJobResult queuePkgCategoryCoverageImportSpreadsheetJob(QueuePkgCategoryCoverageImportSpreadsheetJobRequest request);

    /**
     * <p>Enqueues a request on behalf of the current user to produce a spreadsheet showing which packages have spreadsheets
     * associated with them.  See the {@link JobApi} for details on how to control the job.</p>
     */

    QueuePkgScreenshotSpreadsheetJobResult queuePkgScreenshotSpreadsheetJob(QueuePkgScreenshotSpreadsheetJobRequest request);

    /**
     * <p>Enqueues a request on behalf of the current user to produce a spreadsheet showing which packages have icons
     * associated with them.  See the {@link JobApi} for details on how to control the
     * job.</p>
     */

    QueuePkgIconSpreadsheetJobResult queuePkgIconSpreadsheetJob(QueuePkgIconSpreadsheetJobRequest request);

    /**
     * <p>Enqueues a request on behalf of the current user to produce a spreadsheet showing which packages have what
     * prominence.  See the {@link JobApi} for details on how to control the
     * job.</p>
     */

    QueuePkgProminenceAndUserRatingSpreadsheetJobResult queuePkgProminenceAndUserRatingSpreadsheetJob(QueuePkgProminenceAndUserRatingSpreadsheetJobRequest request);

    /**
     * <p>Enqueues a request to produce an archive of all of the icons of the packages.</p>
     */

    QueuePkgIconExportArchiveJobResult queuePkgIconExportArchiveJob(QueuePkgIconExportArchiveJobRequest request);

    /**
     * <p>Enqueues a request (linked to some data) that will import a tar-ball of data
     * containing package icons.</p>
     */

    QueuePkgIconArchiveImportJobResult queuePkgIconArchiveImportJob(QueuePkgIconArchiveImportJobRequest request);

    /**
     * <p>Enqueues a job to cause all PkgVersion objects with no payload length to get those populated if possible.</p>
     */

    QueuePkgVersionPayloadLengthPopulationJobResult queuePkgVersionPayloadLengthPopulationJob(QueuePkgVersionPayloadLengthPopulationJobRequest request);

    /**
     * <p>Enqueues a job to produce a spreadsheet of the coverage of package version localizations.</p>
     */

    QueuePkgVersionLocalizationCoverageExportSpreadsheetJobResult queuePkgVersionLocalizationCoverageExportSpreadsheetJob(QueuePkgVersionLocalizationCoverageExportSpreadsheetJobRequest request);

    /**
     * <p>Enqueues a job to produce a spreadsheet of the coverage of package localizations.</p>
     */

    QueuePkgLocalizationCoverageExportSpreadsheetJobResult queuePkgLocalizationCoverageExportSpreadsheetJob(QueuePkgLocalizationCoverageExportSpreadsheetJobRequest request);

    /**
     * <p>Enqueues a job to produce a spreadsheet of the coverage of package localizations.</p>
     */

    QueuePkgScreenshotExportArchiveJobResult queuePkgScreenshotExportArchiveJob(QueuePkgScreenshotExportArchiveJobRequest request);

    /**
     * <p>Enqueues a request (linked to some data) that will import a tar-ball of data
     * containing package screenshots.</p>
     */

    QueuePkgScreenshotArchiveImportJobResult queuePkgScreenshotArchiveImportJob(QueuePkgScreenshotArchiveImportJobRequest request);

}
