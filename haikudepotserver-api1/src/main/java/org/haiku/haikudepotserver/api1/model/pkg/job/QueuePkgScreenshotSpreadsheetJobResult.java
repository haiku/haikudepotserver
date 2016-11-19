/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.pkg.job;

import org.haiku.haikudepotserver.api1.model.AbstractQueueJobResult;

public class QueuePkgScreenshotSpreadsheetJobResult extends AbstractQueueJobResult {

    public QueuePkgScreenshotSpreadsheetJobResult() {
    }

    public QueuePkgScreenshotSpreadsheetJobResult(String guid) {
        this.guid = guid;
    }

}
