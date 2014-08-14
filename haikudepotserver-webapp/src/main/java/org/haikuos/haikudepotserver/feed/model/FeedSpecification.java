/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.feed.model;

import java.util.List;

/**
 * <p>This class defines the specification of a feed (rss) from this system.</p>
 *
 * <p>This is unlike other models in that it specifies elements of the system not by their data objects,
 * but their natural references such as package name or natural language code.  This is because there
 * may be a number of packages involved and the system should not fault them all in order to produce an
 * RSS feed.</p>
 */

public class FeedSpecification {

    public enum SupplierType {
        CREATEDPKGVERSION,
        CREATEDUSERRATING
    };

    private String naturalLanguageCode;
    private List<String> pkgNames;
    private Integer limit;
    private List<SupplierType> supplierTypes;

    public String getNaturalLanguageCode() {
        return naturalLanguageCode;
    }

    public void setNaturalLanguageCode(String naturalLanguageCode) {
        this.naturalLanguageCode = naturalLanguageCode;
    }

    public List<String> getPkgNames() {
        return pkgNames;
    }

    public void setPkgNames(List<String> pkgNames) {
        this.pkgNames = pkgNames;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public List<SupplierType> getSupplierTypes() {
        return supplierTypes;
    }

    public void setSupplierTypes(List<SupplierType> supplierTypes) {
        this.supplierTypes = supplierTypes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FeedSpecification that = (FeedSpecification) o;

        if (!limit.equals(that.limit)) return false;
        if (!naturalLanguageCode.equals(that.naturalLanguageCode)) return false;
        if (pkgNames != null ? !pkgNames.equals(that.pkgNames) : that.pkgNames != null) return false;
        if (!supplierTypes.equals(that.supplierTypes)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = naturalLanguageCode.hashCode();
        result = 31 * result + (pkgNames != null ? pkgNames.hashCode() : 0);
        result = 31 * result + limit.hashCode();
        result = 31 * result + supplierTypes.hashCode();
        return result;
    }
}
