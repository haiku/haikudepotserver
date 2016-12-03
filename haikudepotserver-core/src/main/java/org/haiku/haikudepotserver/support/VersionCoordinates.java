/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * <p>This object models the version coordinates of a package.</p>
 */

public class VersionCoordinates {

    private String major;
    private String minor;
    private String micro;
    private String preRelease;
    private Integer revision;

    public VersionCoordinates(org.haiku.pkg.model.PkgVersion version) {
        this(
                version.getMajor(),
                version.getMinor(),
                version.getMicro(),
                version.getPreRelease(),
                version.getRevision());
    }

    public VersionCoordinates(String major, String minor, String micro, String preRelease, Integer revision) {
        Preconditions.checkState(!Strings.isNullOrEmpty(major));
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        this.preRelease = preRelease;
        this.revision = revision;
    }

    public String getMajor() {
        return major;
    }

    public String getMinor() {
        return minor;
    }

    public String getMicro() {
        return micro;
    }

    public String getPreRelease() {
        return preRelease;
    }

    public Integer getRevision() {
        return revision;
    }

    public UriComponentsBuilder appendPathSegments(UriComponentsBuilder builder) {
        builder.pathSegment(getMajor());
        builder.pathSegment(!Strings.isNullOrEmpty(getMinor()) ? getMinor() : "-");
        builder.pathSegment(!Strings.isNullOrEmpty(getMicro()) ? getMicro() : "-");
        builder.pathSegment(!Strings.isNullOrEmpty(getPreRelease()) ? getPreRelease() : "-");
        builder.pathSegment(null!=getRevision() ? Integer.toString(getRevision()) : "-");
        return builder;
    }

    /**
     * <p>Sometimes it is handy to only compare the major, minor and micro portions of the version.  This
     * method will clear those other parts so that they play no role in comparisons etc...</p>
     */

    public VersionCoordinates toVersionCoordinatesWithoutPreReleaseOrRevision() {
        return new VersionCoordinates(
                getMajor(),
                getMinor(),
                getMicro(),
                null,
                null);
    }

    @SuppressWarnings("RedundantIfStatement") // was auto generated!
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VersionCoordinates that = (VersionCoordinates) o;

        if (!major.equals(that.major)) return false;
        if (micro != null ? !micro.equals(that.micro) : that.micro != null) return false;
        if (minor != null ? !minor.equals(that.minor) : that.minor != null) return false;
        if (preRelease != null ? !preRelease.equals(that.preRelease) : that.preRelease != null) return false;
        if (revision != null ? !revision.equals(that.revision) : that.revision != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = major.hashCode();
        result = 31 * result + (minor != null ? minor.hashCode() : 0);
        result = 31 * result + (micro != null ? micro.hashCode() : 0);
        result = 31 * result + (preRelease != null ? preRelease.hashCode() : 0);
        result = 31 * result + (revision != null ? revision.hashCode() : 0);
        return result;
    }

    // log here correlates with the "PackageVersion.cpp" file in the Haiku source
    // code.

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();

        if(null!=getMajor()) {
            result.append(getMajor());
        }
        else {
            result.append('!'); // major is required so need to point this out.
        }

        if(!Strings.isNullOrEmpty(getMinor())) {
            result.append('.');
            result.append(getMinor());

            if(!Strings.isNullOrEmpty(getMicro())) {
                result.append('.');
                result.append(getMicro());
            }
        }

        if(!Strings.isNullOrEmpty(getPreRelease())) {
            result.append('~');
            result.append(getPreRelease());
        }

        if(null!=getRevision() && getRevision() > 0) {
            result.append('-');
            result.append(getRevision().toString());
        }

        return result.toString();
    }

}
