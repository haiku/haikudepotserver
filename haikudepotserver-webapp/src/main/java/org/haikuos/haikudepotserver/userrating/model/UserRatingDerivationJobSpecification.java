/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating.model;

import org.haikuos.haikudepotserver.job.model.AbstractJobSpecification;
import org.haikuos.haikudepotserver.job.model.JobSpecification;

/**
 * <p>An instance of this job can be submitted in order to get the
 * aggregated user rating re-calculated for the package.  It is possible that the job can also be configured
 * such that the derivation is undertaken not for just one package, but for every package in the system.</p>
 */

public class UserRatingDerivationJobSpecification extends AbstractJobSpecification {

    private String pkgName;

    public UserRatingDerivationJobSpecification() {
    }

    public UserRatingDerivationJobSpecification(String pkgName) {
        super();
        this.pkgName = pkgName;
    }

    public String getPkgName() {
        return pkgName;
    }

    public void setPkgName(String value) {
        this.pkgName = value;
    }

    public Long getTimeToLive() {
        return 120 * 1000L; // only stay around for a short while
    }

    public boolean appliesToAllPkgs() {
        return null==getPkgName();
    }

    @Override
    public boolean isEquivalent(JobSpecification other) {
        if(UserRatingDerivationJobSpecification.class.isAssignableFrom(other.getClass())) {
            UserRatingDerivationJobSpecification other2 = (UserRatingDerivationJobSpecification) other;

            if(null==other2.getPkgName()) {
                return null==getPkgName();
            }
            else {
                return null!=getPkgName() && other2.getPkgName().equals(getPkgName());
            }
        }

        return false;
    }

}
