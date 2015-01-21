/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.model;

import org.haikuos.haikudepotserver.job.model.AbstractJobSpecification;
import org.haikuos.haikudepotserver.job.model.JobSpecification;

import java.util.Objects;

public class PkgVersionPayloadLengthPopulationJobSpecification extends AbstractJobSpecification {

    @Override
    public boolean isEquivalent(JobSpecification other) {
        if(PkgVersionPayloadLengthPopulationJobSpecification.class.isAssignableFrom(other.getClass())) {
            PkgScreenshotOptimizationJobSpecification spec = (PkgScreenshotOptimizationJobSpecification) other;
            return Objects.equals(spec.getOwnerUserNickname(), getOwnerUserNickname());
        }

        return false;
    }

}
