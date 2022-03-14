/*
 * Copyright 2014-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.userrating.model;

import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobSpecification;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

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

    public Optional<Long> tryGetTimeToLiveMillis() {
        return Optional.of(TimeUnit.MILLISECONDS.convert(120, TimeUnit.SECONDS)); // only stay around for a short while
    }

    public boolean appliesToAllPkgs() {
        return null==getPkgName();
    }

    @Override
    public boolean isEquivalent(JobSpecification other) {
        if(super.isEquivalent(other)) {
            UserRatingDerivationJobSpecification other2 = (UserRatingDerivationJobSpecification) other;
            return StringUtils.equals(other2.getPkgName(), getPkgName());
        }

        return false;
    }

}
