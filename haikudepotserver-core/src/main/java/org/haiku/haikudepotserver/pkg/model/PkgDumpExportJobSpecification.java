/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobSpecification;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class PkgDumpExportJobSpecification extends AbstractJobSpecification {

    private final static long TTL_MINUTES = 30;

    private String repositorySourceCode;

    private String naturalLanguageCode;

    @Override
    public Optional<Long> tryGetTimeToLiveMillis() {
        return Optional.of(TimeUnit.MINUTES.toMillis(TTL_MINUTES));
    }

    public String getRepositorySourceCode() {
        return repositorySourceCode;
    }

    public void setRepositorySourceCode(String value) {
        this.repositorySourceCode = value;
    }

    public String getNaturalLanguageCode() {
        return naturalLanguageCode;
    }

    public void setNaturalLanguageCode(String naturalLanguageCode) {
        this.naturalLanguageCode = naturalLanguageCode;
    }

    public boolean isEquivalent(JobSpecification other) {
        if (!super.isEquivalent(other)) {
            return false;
        }

        PkgDumpExportJobSpecification pkgOther = PkgDumpExportJobSpecification.class.cast(other);
        return Objects.equals(pkgOther.getNaturalLanguageCode(), getNaturalLanguageCode())
                && Objects.equals(pkgOther.getRepositorySourceCode(), getRepositorySourceCode());
    }

}
