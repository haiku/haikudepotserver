/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.model;

import org.haikuos.haikudepotserver.job.model.AbstractJobSpecification;
import org.haikuos.haikudepotserver.job.model.JobSpecification;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class PkgScreenshotOptimizationJobSpecification extends AbstractJobSpecification {

    private Set<String> pkgScreenshotCodes;

    public PkgScreenshotOptimizationJobSpecification() {
    }

    public PkgScreenshotOptimizationJobSpecification(String pkgScreenshotCode) {
        this.pkgScreenshotCodes = Collections.singleton(pkgScreenshotCode);
    }

    public PkgScreenshotOptimizationJobSpecification(Set<String> pkgScreenshotCodes) {
        this.pkgScreenshotCodes = pkgScreenshotCodes;
    }

    public Set<String> getPkgScreenshotCodes() {
        return pkgScreenshotCodes;
    }

    public void setPkgScreenshotCodes(Set<String> pkgScreenshotCodes) {
        this.pkgScreenshotCodes = pkgScreenshotCodes;
    }

    @Override
    public boolean isEquivalent(JobSpecification other) {
        if(PkgScreenshotOptimizationJobSpecification.class.isAssignableFrom(other.getClass())) {
            PkgScreenshotOptimizationJobSpecification spec = (PkgScreenshotOptimizationJobSpecification) other;
            return
                    Objects.equals(spec.getOwnerUserNickname(), getOwnerUserNickname()) &&
                    Objects.equals(spec.getPkgScreenshotCodes(), getPkgScreenshotCodes());
        }

        return false;
    }

}
