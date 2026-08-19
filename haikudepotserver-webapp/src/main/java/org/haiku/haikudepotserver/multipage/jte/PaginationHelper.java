/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.jte;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.multipage.model.Page;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class PaginationHelper {

    /**
     * Injects the page into the supplied {@link UriComponents} to create a new URL.
     */
    public static String urlAtPage(UriComponents uri, String queryParam, Page page) {
        Preconditions.checkArgument(null != uri, "uri may not be null");
        Preconditions.checkArgument(StringUtils.isNotBlank(queryParam), "query param may not be empty");
        Preconditions.checkArgument(null != page, "page may not be null");
        return
                UriComponentsBuilder.newInstance().uriComponents(uri)
                        .replaceQueryParam(queryParam, page.offset())
                        .build()
                        .toString();
    }

}
