/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.pkg;

import org.haiku.haikudepotserver.api1.model.AbstractQueueJobResult;

public class QueuePkgCategoryCoverageImportSpreadsheetJobResult extends AbstractQueueJobResult {

    public QueuePkgCategoryCoverageImportSpreadsheetJobResult() {
    }

    public QueuePkgCategoryCoverageImportSpreadsheetJobResult(String guid) {
        this.guid = guid;
    }

}
