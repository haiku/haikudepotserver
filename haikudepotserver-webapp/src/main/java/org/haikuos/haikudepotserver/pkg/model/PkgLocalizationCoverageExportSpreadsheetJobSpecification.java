/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.model;

import org.haikuos.haikudepotserver.job.model.AbstractJobSpecification;
import org.haikuos.haikudepotserver.job.model.JobSpecification;
import org.springframework.util.ObjectUtils;

public class PkgLocalizationCoverageExportSpreadsheetJobSpecification extends AbstractJobSpecification {

    @Override
    public boolean isEquivalent(JobSpecification other) {
        if(PkgLocalizationCoverageExportSpreadsheetJobSpecification.class.isAssignableFrom(other.getClass())) {
            return ObjectUtils.nullSafeEquals(other.getOwnerUserNickname(), getOwnerUserNickname());
        }

        return false;
    }

}
