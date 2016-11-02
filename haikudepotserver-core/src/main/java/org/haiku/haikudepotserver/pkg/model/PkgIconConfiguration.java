/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import com.google.common.base.Preconditions;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
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
        return ComparisonChain.start()
                .compare(o.getMediaType().getCode(), getMediaType().getCode())
                .compare(o.getSize(), getSize(), Ordering.natural().nullsFirst())
                .result();
    }
}
