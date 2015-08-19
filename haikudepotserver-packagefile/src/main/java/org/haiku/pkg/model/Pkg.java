/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.model;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Pkg {

    private String name;
    private PkgVersion version;
    private PkgArchitecture architecture;
    private String vendor;
    private List<String> copyrights;
    private List<String> licenses;
    private String summary;
    private String description;
    private PkgUrl homePageUrl;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public PkgVersion getVersion() {
        return version;
    }

    public void setVersion(PkgVersion version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PkgArchitecture getArchitecture() {
        return architecture;
    }

    public void setArchitecture(PkgArchitecture architecture) {
        this.architecture = architecture;
    }

    public List<String> getCopyrights() {
        if(null==copyrights) {
            return Collections.emptyList();
        }
        return copyrights;
    }

    public void addCopyright(String copyright) {
        Preconditions.checkNotNull(copyright);

        if(null==copyrights) {
            copyrights = new ArrayList<>();
        }

        copyrights.add(copyright);
    }

    public List<String> getLicenses() {
        if(null==licenses) {
            return Collections.emptyList();
        }
        return licenses;
    }

    public void addLicense(String license) {
        Preconditions.checkNotNull(license);

        if(null==licenses) {
            licenses = new ArrayList<>();
        }

        licenses.add(license);
    }

    public PkgUrl getHomePageUrl() {
        return homePageUrl;
    }

    public void setHomePageUrl(PkgUrl homePageUrl) {
        this.homePageUrl = homePageUrl;
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(null==name?"???":name);
        stringBuilder.append(" : ");
        stringBuilder.append(null==version?"???":version.toString());
        stringBuilder.append(" : ");
        stringBuilder.append(null==architecture?"???":getArchitecture().toString());
        return stringBuilder.toString();
    }

}
