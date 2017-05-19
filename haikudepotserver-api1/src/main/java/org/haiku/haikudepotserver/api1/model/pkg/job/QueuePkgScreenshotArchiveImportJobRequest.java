/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.pkg.job;

public class QueuePkgScreenshotArchiveImportJobRequest {

    /**
     * See &quot;PkgScreenshotImportArchiveJobSpecification&quot; for more information about this.
     */

    public enum ImportStrategy {
        AUGMENT,
        REPLACE
    }

    public String inputDataGuid;

    public ImportStrategy importStrategy;

}
