/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class VersionCoordinates {

    private String major;
    private String minor;
    private String micro;
    private String preRelease;
    private Integer revision;

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

    @Override
    public String toString() {
        return getMajor() + '.' + getMinor() + '.' + getMicro() + '.' + getPreRelease() + '.' + getRevision();
    }

}
