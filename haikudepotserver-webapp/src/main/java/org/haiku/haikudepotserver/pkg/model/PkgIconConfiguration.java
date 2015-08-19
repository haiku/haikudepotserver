/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.dataobjects.MediaType;

/**
 * <p>A package icon has a media type and a size.  This model class simply
 * combines those two together.</p>
 */
public class PkgIconConfiguration implements Comparable<PkgIconConfiguration> {

    private MediaType mediaType;
    private Integer size;

    public PkgIconConfiguration(MediaType mediaType, Integer size) {
        Preconditions.checkState(null!=mediaType, "the media type is required");
        Preconditions.checkState(null==size || size > 0, "illegal size value for icon configuration");
        this.mediaType = mediaType;
        this.size = size;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public Integer getSize() {
        return size;
    }

    @Override
    public String toString() {
        return String.format(
                "%s @ %s",
                getMediaType().getCode(),
                getSize().toString());
    }

    @Override
    public int compareTo(PkgIconConfiguration o) {
        int result = o.getMediaType().getCode().compareTo(getMediaType().getCode());

        if(0==result) {
            if((null==o.getSize()) != (null==getSize())) {
                if(null==getSize()) {
                    result = 1;
                }
                else {
                    result = -1;
                }
            }
            else {
                if(null!=getSize()) {
                    result = o.getSize().compareTo(getSize());
                }
            }
        }

        return result;
    }
}
