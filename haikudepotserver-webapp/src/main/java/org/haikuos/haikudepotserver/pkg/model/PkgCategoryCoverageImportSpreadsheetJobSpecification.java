/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.model;

import com.google.common.base.Strings;
import org.haikuos.haikudepotserver.job.model.AbstractJobSpecification;
import org.haikuos.haikudepotserver.job.model.JobSpecification;
import org.springframework.util.ObjectUtils;

import java.util.Collection;
import java.util.Collections;

public class PkgCategoryCoverageImportSpreadsheetJobSpecification extends AbstractJobSpecification {

    /**
     * <p>This identifies the spreadsheet input data that can be used to work off.</p>
     */

    private String inputDataGuid;

    public String getInputDataGuid() {
        return inputDataGuid;
    }

    public void setInputDataGuid(String inputDataGuid) {
        this.inputDataGuid = inputDataGuid;
    }

    public Collection<String> getSuppliedDataGuids() {
        if(!Strings.isNullOrEmpty(inputDataGuid)) {
            return Collections.singleton(inputDataGuid);
        }

        return super.getSuppliedDataGuids();
    }

    @Override
    public boolean isEquivalent(JobSpecification other) {
        return
                PkgCategoryCoverageImportSpreadsheetJobSpecification.class.isAssignableFrom(other.getClass())
                        && getSuppliedDataGuids().equals(other.getSuppliedDataGuids())
                        && ObjectUtils.nullSafeEquals(other.getOwnerUserNickname(), getOwnerUserNickname());
    }

}
