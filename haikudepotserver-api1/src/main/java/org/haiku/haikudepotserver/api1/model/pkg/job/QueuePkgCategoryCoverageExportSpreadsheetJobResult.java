/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.pkg.job;

import org.haiku.haikudepotserver.api1.model.AbstractQueueJobResult;

public class QueuePkgCategoryCoverageExportSpreadsheetJobResult extends AbstractQueueJobResult {

    public QueuePkgCategoryCoverageExportSpreadsheetJobResult() {
    }

    public QueuePkgCategoryCoverageExportSpreadsheetJobResult(String guid) {
        this.guid = guid;
    }

}
