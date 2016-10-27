/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import org.haiku.haikudepotserver.api1.model.userrating.job.QueueUserRatingSpreadsheetJobRequest;
import org.haiku.haikudepotserver.api1.model.userrating.job.QueueUserRatingSpreadsheetJobResult;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;

public interface UserRatingJobApi {

    /**
     * <p>Enqueues a request to run a report about user ratings such that they are output as a spreadsheet.  The
     * result contains a GUID that is a handle to the job.  The job is able to be managed by the
     * {@link JobApi}.
     */

    QueueUserRatingSpreadsheetJobResult queueUserRatingSpreadsheetJob(QueueUserRatingSpreadsheetJobRequest request) throws ObjectNotFoundException;


}
