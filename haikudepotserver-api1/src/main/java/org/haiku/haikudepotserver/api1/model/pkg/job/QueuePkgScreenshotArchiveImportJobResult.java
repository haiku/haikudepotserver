/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.pkg.job;

import org.haiku.haikudepotserver.api1.model.AbstractQueueJobResult;

public class QueuePkgScreenshotArchiveImportJobResult extends AbstractQueueJobResult {

    public QueuePkgScreenshotArchiveImportJobResult() {
    }

    public QueuePkgScreenshotArchiveImportJobResult(String guid) {
        this.guid = guid;
    }

}
