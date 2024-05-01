/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class PkgVersion {

    private final String major;
    private final String minor;
    private final String micro;
    private final String preRelease;
    private final Integer revision;

    public PkgVersion(String major, String minor, String micro, String preRelease, Integer revision) {
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

    private void appendDotValue(StringBuilder stringBuilder, String value) {
        if(!stringBuilder.isEmpty()) {
            stringBuilder.append('.');
        }

        if(null != value) {
            stringBuilder.append(value);
        }
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        appendDotValue(result,getMajor());
        appendDotValue(result,getMinor());
        appendDotValue(result,getMicro());
        appendDotValue(result, getPreRelease());
        appendDotValue(result, null == getRevision() ? null : getRevision().toString());
        return result.toString();
    }

}
