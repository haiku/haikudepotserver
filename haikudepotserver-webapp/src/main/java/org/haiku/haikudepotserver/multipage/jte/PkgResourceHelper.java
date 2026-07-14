/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.jte;

import com.google.common.base.Preconditions;
import io.micrometer.common.util.StringUtils;
import org.haiku.haikudepotserver.pkg.controller.PkgIconController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;

public class PkgResourceHelper {

    public static String pkgIconUrl(String pkgName, int size, Object modifyTimestamp) {
        Preconditions.checkArgument(StringUtils.isNotBlank(pkgName));
        Preconditions.checkArgument(size > 0);
        Preconditions.checkArgument(size < 1024); // preposterous size

        return
                UriComponentsBuilder.newInstance()
                        .pathSegment(PkgIconController.SEGMENT_PKGICON, pkgName + ".png")
                        .queryParam(PkgIconController.KEY_FALLBACK, "true")
                        .queryParam(PkgIconController.KEY_SIZE, size == 16 || size == 32 ? size * 2 : size)
                        .queryParam("m", null == modifyTimestamp ? 0 : modifyTimestamp.hashCode())
                        .build()
                        .toString();
    }

}
