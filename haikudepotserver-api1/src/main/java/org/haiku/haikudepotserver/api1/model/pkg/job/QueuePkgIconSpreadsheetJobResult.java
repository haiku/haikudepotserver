/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.pkg.job;

import org.haiku.haikudepotserver.api1.model.AbstractQueueJobResult;

public class QueuePkgIconSpreadsheetJobResult extends AbstractQueueJobResult {

    public QueuePkgIconSpreadsheetJobResult() {
    }

    public QueuePkgIconSpreadsheetJobResult(String guid) {
        this.guid = guid;
    }

}
