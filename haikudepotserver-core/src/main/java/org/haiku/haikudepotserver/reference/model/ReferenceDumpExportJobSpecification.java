/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.reference.model;

import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobSpecification;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ReferenceDumpExportJobSpecification extends AbstractJobSpecification {

    private final static long TTL_MINUTES = 30;

    private String naturalLanguageCode;

    /**
     * <p>This flag will control that only natural languages with two-character codes will be returned; no script
     * or countries are allowed. This is a temporary arrangement to support older HaikuDepot clients that cannot
     * deal with more complex language codes. After R1B5 this can probably be removed.</p>
     */
    @Deprecated
    private boolean filterForSimpleTwoCharLanguageCodes = false;

    @Override
    public Optional<Long> tryGetTimeToLiveMillis() {
        return Optional.of(TimeUnit.MINUTES.toMillis(TTL_MINUTES));
    }

    public String getNaturalLanguageCode() {
        return naturalLanguageCode;
    }

    public void setNaturalLanguageCode(String naturalLanguageCode) {
        this.naturalLanguageCode = naturalLanguageCode;
    }

    @Deprecated
    public boolean isFilterForSimpleTwoCharLanguageCodes() {
        return filterForSimpleTwoCharLanguageCodes;
    }

    @Deprecated
    public void setFilterForSimpleTwoCharLanguageCodes(boolean filterForSimpleTwoCharLanguageCodes) {
        this.filterForSimpleTwoCharLanguageCodes = filterForSimpleTwoCharLanguageCodes;
    }

    public boolean isEquivalent(JobSpecification other) {
        if (!super.isEquivalent(other)) {
            return false;
        }

        ReferenceDumpExportJobSpecification pkgOther = ReferenceDumpExportJobSpecification.class.cast(other);
        return Objects.equals(pkgOther.getNaturalLanguageCode(), getNaturalLanguageCode())
                && pkgOther.isFilterForSimpleTwoCharLanguageCodes() == isFilterForSimpleTwoCharLanguageCodes();
    }

}
