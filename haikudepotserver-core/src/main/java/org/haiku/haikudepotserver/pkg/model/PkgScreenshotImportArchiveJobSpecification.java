/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import com.google.common.base.Strings;
import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobSpecification;
import org.springframework.util.ObjectUtils;

import java.util.Collection;
import java.util.Collections;

public class PkgScreenshotImportArchiveJobSpecification extends AbstractJobSpecification {

    public enum ImportStrategy {

        /**
         * <p>This option means that the imported screenshots will be added to the screenshots
         * that are already there.  This approach will however check to see if the screenshot
         * is already there to avoid duplicates.</p>
         */

        AUGMENT,

        /**
         * <p>This option means that all of the screenshots for the package will first be
         * removed and then the new screenshot will be added back in.  Any existing screenshots
         * that are identical to the new ones will be retained with the same GUID.</p>
         */

        REPLACE
    }

    private ImportStrategy importStrategy;

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

    public ImportStrategy getImportStrategy() {
        return importStrategy;
    }

    public void setImportStrategy(ImportStrategy importStrategy) {
        this.importStrategy = importStrategy;
    }

    public Collection<String> getSuppliedDataGuids() {
        if(!Strings.isNullOrEmpty(inputDataGuid)) {
            return Collections.singleton(inputDataGuid);
        }

        return super.getSuppliedDataGuids();
    }

    @Override
    public boolean isEquivalent(JobSpecification other) {
        if (!super.isEquivalent(other)) {
            return false;
        }

        PkgScreenshotImportArchiveJobSpecification otherSpecific = PkgScreenshotImportArchiveJobSpecification.class.cast(other);

        return
                getImportStrategy().equals(otherSpecific.getImportStrategy())
                        && getSuppliedDataGuids().equals(otherSpecific.getSuppliedDataGuids())
                        && ObjectUtils.nullSafeEquals(otherSpecific.getOwnerUserNickname(), getOwnerUserNickname());
    }

}
