/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.web.model;

import java.util.List;

/**
 * <p>This object identifies a number of web resources that can be grouped together.  They are grouped in this way
 * such that they can be referenced once in the application for download; either splayed out as individual requests
 * or as a composite download.</p>
 */

public class WebResourceGroup {

    private String code;

    private List<String> resources;

    private String mimeType;

    private Boolean separated;

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mediaType) {
        this.mimeType = mediaType;
    }

    public Boolean getSeparated() {
        return separated;
    }

    public void setSeparated(Boolean separated) {
        this.separated = separated;
    }

    public List<String> getResources() {
        return resources;
    }

    public void setResources(List<String> resources) {
        this.resources = resources;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return String.format("%s; %d web resources",getCode(),null!=getResources()?getResources().size():0);
    }

}
