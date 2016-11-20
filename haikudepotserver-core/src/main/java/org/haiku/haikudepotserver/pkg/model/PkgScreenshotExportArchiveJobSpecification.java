/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobSpecification;

import java.util.Objects;

public class PkgScreenshotExportArchiveJobSpecification extends AbstractJobSpecification {

    private String pkgName;

    public String getPkgName() {
        return pkgName;
    }

    public void setPkgName(String pkgName) {
        this.pkgName = pkgName;
    }

    @Override
    public boolean isEquivalent(JobSpecification other) {
        return super.isEquivalent(other) &&
                Objects.equals(getPkgName(), PkgScreenshotExportArchiveJobSpecification.class.cast(other).getPkgName());
    }


}
