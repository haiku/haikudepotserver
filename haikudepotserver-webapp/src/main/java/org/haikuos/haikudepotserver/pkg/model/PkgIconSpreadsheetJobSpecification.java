/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.model;

import org.haikuos.haikudepotserver.job.model.AbstractJobSpecification;
import org.haikuos.haikudepotserver.job.model.JobSpecification;

import java.util.Objects;

public class PkgIconSpreadsheetJobSpecification extends AbstractJobSpecification {

    @Override
    public boolean isEquivalent(JobSpecification other) {
        if(PkgIconSpreadsheetJobSpecification.class.isAssignableFrom(other.getClass())) {
            return Objects.equals(other.getOwnerUserNickname(), getOwnerUserNickname());
        }

        return false;
    }

}
