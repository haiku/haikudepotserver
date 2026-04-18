/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.reference.model;

import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobSpecification;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ReferenceDumpExportJobSpecification extends AbstractJobSpecification {

    /**
     * <p>The reference data is able to be left for a long time as it rarely changes.</p>
     */
    private final static long TTL_HOURS = 24;

    private String naturalLanguageCode;

    /**
     * <p>This is the version of the HDS project. The reference data could change between versions of HDS because new
     * reference data can be applied in a data migration.</p>
     */
    private String projectVersion;

    /**
     * <p>This flag will control that only natural languages with two-character codes will be returned; no script
     * or countries are allowed. This is a temporary arrangement to support older HaikuDepot clients that cannot
     * deal with more complex language codes. After R1B5 this can probably be removed.</p>
     */
    @Deprecated
    private boolean filterForSimpleTwoCharLanguageCodes = false;

    @Override
    public Optional<Long> tryGetTimeToLiveMillis() {
        return Optional.of(TimeUnit.HOURS.toMillis(TTL_HOURS) + createTimeToLiveJitterMillis(TimeUnit.HOURS));
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

    public String getProjectVersion() {
        return projectVersion;
    }

    public void setProjectVersion(String projectVersion) {
        this.projectVersion = projectVersion;
    }

    public boolean isEquivalent(JobSpecification other) {
        if (!super.isEquivalent(other)) {
            return false;
        }

        ReferenceDumpExportJobSpecification pkgOther = (ReferenceDumpExportJobSpecification) other;
        return Objects.equals(pkgOther.getNaturalLanguageCode(), getNaturalLanguageCode())
                && Objects.equals(pkgOther.getProjectVersion(), getProjectVersion())
                && pkgOther.isFilterForSimpleTwoCharLanguageCodes() == isFilterForSimpleTwoCharLanguageCodes();
    }

    @Override
    public Map<String, String> getTags() {
        return Map.of(
                TAG_NATURAL_LANGUAGE_CODE,
                getNaturalLanguageCode()
        );
    }

}
