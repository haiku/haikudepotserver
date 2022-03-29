/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.model;

import java.util.Collections;
import java.util.List;

public class Pkg {

    private final String name;
    private final PkgVersion version;
    private final PkgArchitecture architecture;
    private final String vendor;
    private final List<String> copyrights;
    private final List<String> licenses;
    private final String summary;
    private final String description;
    private final PkgUrl homePageUrl;

    public Pkg(
            String name,
            PkgVersion version,
            PkgArchitecture architecture,
            String vendor,
            List<String> copyrights,
            List<String> licenses,
            String summary,
            String description,
            PkgUrl homePageUrl) {
        this.name = name;
        this.version = version;
        this.architecture = architecture;
        this.vendor = vendor;
        this.copyrights = copyrights;
        this.licenses = licenses;
        this.summary = summary;
        this.description = description;
        this.homePageUrl = homePageUrl;
    }

    public String getDescription() {
        return description;
    }

    public String getSummary() {
        return summary;
    }

    public String getVendor() {
        return vendor;
    }

    public PkgVersion getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public PkgArchitecture getArchitecture() {
        return architecture;
    }

    public List<String> getCopyrights() {
        if(null == copyrights) {
            return Collections.emptyList();
        }
        return copyrights;
    }

    public List<String> getLicenses() {
        if(null == licenses) {
            return Collections.emptyList();
        }
        return licenses;
    }

    public PkgUrl getHomePageUrl() {
        return homePageUrl;
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(null == name ? "???" : name);
        stringBuilder.append(" : ");
        stringBuilder.append(null == version ? "???" : version.toString());
        stringBuilder.append(" : ");
        stringBuilder.append(null == architecture ? "???" : getArchitecture().toString());
        return stringBuilder.toString();
    }

}
