/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.pkg.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class PkgUrl {

    private String url;

    private PkgUrlType urlType;

    public PkgUrl(String url, PkgUrlType urlType) {
        super();
        Preconditions.checkNotNull(urlType);
        Preconditions.checkNotNull(url);
        Preconditions.checkState(!Strings.isNullOrEmpty(url));
        this.url = url;
        this.urlType = urlType;
    }

    public PkgUrlType getUrlType() {
        return urlType;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return String.format("%s; %s",urlType.toString(),url);
    }

}
