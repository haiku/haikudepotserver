/*
 * Copyright 2013-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.PkgCategory;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.pkg.PkgServiceImpl;
import org.haiku.haikudepotserver.support.AbstractSearchSpecification;

import java.util.Collection;
import java.util.List;

/**
 * <p>This model object specifies the parameters of a search into the system for packages.  See the
 * {@link PkgServiceImpl} for further detail on this.</p>
 */

public class PkgSearchSpecification extends AbstractSearchSpecification {

    public enum SortOrdering {
        NAME,
        PROMINENCE,
        VERSIONCREATETIMESTAMP,
        VERSIONVIEWCOUNTER
    }

    /**
     * @since 2015-05-27
     */

    private List<Repository> repositories;

    /**
     * @since 2022-03-26
     */

    private Architecture architecture;

    private PkgCategory pkgCategory;

    private Number daysSinceLatestVersion;

    private SortOrdering sortOrdering;

    private NaturalLanguage naturalLanguage;

    private Boolean includeDevelopment;

    private Boolean onlyDesktop;

    private Boolean onlyNativeDesktop;

    public List<Repository> getRepositories() {
        return repositories;
    }

    public void setRepositories(Collection<Repository> repositories) {
        this.repositories = List.copyOf(repositories);
    }

    public NaturalLanguage getNaturalLanguage() {
        return naturalLanguage;
    }

    public void setNaturalLanguage(NaturalLanguage naturalLanguage) {
        this.naturalLanguage = naturalLanguage;
    }

    public Architecture getArchitecture() {
        return architecture;
    }

    public void setArchitecture(Architecture architecture) {
        this.architecture = architecture;
    }

    public PkgCategory getPkgCategory() {
        return pkgCategory;
    }

    public void setPkgCategory(PkgCategory pkgCategory) {
        this.pkgCategory = pkgCategory;
    }

    public SortOrdering getSortOrdering() {
        return sortOrdering;
    }

    public void setSortOrdering(SortOrdering sortOrdering) {
        this.sortOrdering = sortOrdering;
    }

    public Number getDaysSinceLatestVersion() {
        return daysSinceLatestVersion;
    }

    public void setDaysSinceLatestVersion(Number daysSinceLatestVersion) {
        this.daysSinceLatestVersion = daysSinceLatestVersion;
    }

    public Boolean getIncludeDevelopment() {
        return includeDevelopment;
    }

    public void setIncludeDevelopment(Boolean includeDevelopment) {
        this.includeDevelopment = includeDevelopment;
    }

    public Boolean getOnlyNativeDesktop() {
        return onlyNativeDesktop;
    }

    public void setOnlyNativeDesktop(Boolean onlyNativeDesktop) {
        this.onlyNativeDesktop = onlyNativeDesktop;
    }

    public Boolean getOnlyDesktop() {
        return onlyDesktop;
    }

    public void setOnlyDesktop(Boolean onlyDesktop) {
        this.onlyDesktop = onlyDesktop;
    }
}
