/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import com.google.common.base.Strings;
import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobSpecification;
import org.springframework.util.ObjectUtils;

import java.util.Collection;
import java.util.Collections;

public class PkgIconImportArchiveJobSpecification extends AbstractJobSpecification {

    /**
     * <p>This identifier points to the tar-ball data that has previously been uploaded.</p>
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
                super.isEquivalent(other)
                        && getSuppliedDataGuids().equals(other.getSuppliedDataGuids())
                        && ObjectUtils.nullSafeEquals(other.getOwnerUserNickname(), getOwnerUserNickname());
    }

}
