/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.model;

import com.google.common.base.Optional;
import org.haikuos.haikudepotserver.model.auto._PkgIcon;

import java.util.List;

public class PkgIcon extends _PkgIcon {

    /**
     * <p>As there should be only one of these, if there are two then this method will throw an
     * {@link IllegalStateException}.</p>
     */

    public Optional<PkgIconImage> getPkgIconImage() {
        List<PkgIconImage> images = getPkgIconImages();

        switch(images.size()) {
            case 0: return Optional.absent();
            case 1: return Optional.of(images.get(0));
            default:
                throw new IllegalStateException("more than one pkg icon image found on an icon image");
        }
    }

}
