/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

import org.haikuos.haikudepotserver.api1.model.AbstractQueueJobResult;

public class QueuePkgIconExportArchiveJobResult extends AbstractQueueJobResult {

    public QueuePkgIconExportArchiveJobResult() {
    }

    public QueuePkgIconExportArchiveJobResult(String guid) {
        this.guid = guid;
    }

}
