/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.feed;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.feed.model.FeedService;
import org.haiku.haikudepotserver.feed.model.FeedSpecification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.stream.Collectors;

@Service
public class FeedServiceImpl implements FeedService {

    private final String baseUrl;

    public FeedServiceImpl(
            @Value("${hds.base-url}") String baseUrl
    ) {
        this.baseUrl = Preconditions.checkNotNull(baseUrl);
    }

    /**
     * <p>Given a specification for a feed, this method will generate a URL that external users can query in order
     * to get that feed.</p>
     */

    @Override
    public String generateUrl(FeedSpecification specification) {
        Preconditions.checkNotNull(specification);

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(baseUrl)
                .path(PATH_ROOT + "/pkg.atom");

        if(null != specification.getNaturalLanguageCoordinates()) {
            builder.queryParam(KEY_NATURALLANGUAGECODE, specification.getNaturalLanguageCoordinates().getCode());
        }

        if(null != specification.getLimit()) {
            builder.queryParam(KEY_LIMIT, specification.getLimit().toString());
        }

        if(null != specification.getSupplierTypes()) {
            builder.queryParam(
                    KEY_TYPES,
                    specification.getSupplierTypes()
                            .stream()
                            .map(FeedSpecification.SupplierType::name)
                            .collect(Collectors.joining(","))
            );
        }

        if(null != specification.getPkgNames()) {
            // split on hyphens because hyphens are not allowed in package names
            builder.queryParam(KEY_PKGNAMES, String.join("-", specification.getPkgNames()));
        }

        return builder.build().toString();
    }

}
