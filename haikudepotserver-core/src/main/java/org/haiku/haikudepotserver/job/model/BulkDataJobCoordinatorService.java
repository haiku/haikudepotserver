/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.model;

import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;

public interface BulkDataJobCoordinatorService {

    /**
     * <p>This method will look at the currently stored jobs and will make sure
     * that published data is up to date.</p>
     */

    void performRefresh();

    /**
     * <p>This is over and above the regular Job garbage collection because it will
     * not only look at Jobs that have expired in and of themselves, but also which
     * jobs are no longer required for bulk data purposes.</p>
     */
    void clearExpiredJobs();

    /**
     * <p>Obtains the most recent Job for the packages' icons. If there is
     * none then the method will create a Job and return that.</p>
     */

    String getOrCreatePkgIconExportArchive();

    /**
     * <p>Obtains the most recent {@link JobSnapshot} for the Repository Dump Export.
     * If there is none then it will create a Job and return that.</p>
     */

    String getOrCreateRepositoryDumpExport();

    /**
     * <p>Obtains the most recent {@link JobSnapshot} for the Reference Dump Export.
     * If there is none then it will create a Job and return that.</p>
     */

    String getOrCreateReferenceDumpExport(NaturalLanguageCoordinates naturalLanguage);

    /**
     * <p>Obtains the most recent {@link JobSnapshot} for the Pkg Dump Export.
     * If there is none then it will create a Job and return that.</p>
     */

    String getOrCreatePkgDumpExport(NaturalLanguageCoordinates naturalLanguage, String repositorySourceCode);

}
