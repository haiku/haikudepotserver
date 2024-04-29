/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.pkg.model;

import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;

public class PkgDumpLocalizationImportJobSpecification extends AbstractJobSpecification {

    private String inputDataGuid;

    private String originSystemDescription;

    public String getInputDataGuid() {
        return inputDataGuid;
    }

    public void setInputDataGuid(String inputDataGuid) {
        this.inputDataGuid = inputDataGuid;
    }

    public String getOriginSystemDescription() {
        return originSystemDescription;
    }

    public void setOriginSystemDescription(String originSystemDescription) {
        this.originSystemDescription = originSystemDescription;
    }
}
