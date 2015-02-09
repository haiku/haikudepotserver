/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

import org.haikuos.haikudepotserver.api1.model.AbstractQueueJobResult;

public class QueuePkgCategoryCoverageExportSpreadsheetJobResult extends AbstractQueueJobResult {

    public QueuePkgCategoryCoverageExportSpreadsheetJobResult() {
    }

    public QueuePkgCategoryCoverageExportSpreadsheetJobResult(String guid) {
        this.guid = guid;
    }

}
