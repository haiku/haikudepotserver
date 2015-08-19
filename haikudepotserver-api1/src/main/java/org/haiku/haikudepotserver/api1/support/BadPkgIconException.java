/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.support;

/**
 * <p>This exception is thrown when there is some issue with the format of the icon data or the size of it.</p>
 */

public class BadPkgIconException extends Exception {

    private String mediaTypeCode;
    private Integer size;

    public BadPkgIconException(String mediaTypeCode, Integer size) {
        this.mediaTypeCode = mediaTypeCode;
        this.size = size;
    }

    public BadPkgIconException(String mediaTypeCode, Integer size, Throwable cause) {
        super(cause);
        this.mediaTypeCode = mediaTypeCode;
        this.size = size;
    }

    public String getMediaTypeCode() {
        return mediaTypeCode;
    }

    public Integer getSize() {
        return size;
    }

}
