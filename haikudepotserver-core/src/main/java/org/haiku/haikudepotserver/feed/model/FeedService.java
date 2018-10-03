/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.feed.model;

public interface FeedService {

    String KEY_NATURALLANGUAGECODE = "natlangcode";
    String KEY_PKGNAMES = "pkgnames";
    String KEY_LIMIT = "limit";
    String KEY_TYPES = "types";
    String KEY_EXTENSION = "extension";

    String PATH_ROOT = "/__feed";

    /**
     * <p>Given a specification for a feed, this method will generate a URL that external users can query in order
     * to get that feed.</p>
     */

    String generateUrl(FeedSpecification specification);

}
