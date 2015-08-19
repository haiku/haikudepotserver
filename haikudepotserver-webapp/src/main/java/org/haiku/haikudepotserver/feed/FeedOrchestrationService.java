/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.feed;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.feed.controller.FeedController;
import org.haiku.haikudepotserver.feed.model.FeedSpecification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.stream.Collectors;

@Service
public class FeedOrchestrationService {

    @Value("${baseurl}")
    private String baseUrl;

    /**
     * <p>Given a specification for a feed, this method will generate a URL that external users can query in order
     * to get that feed.</p>
     */

    public String generateUrl(FeedSpecification specification) {
        Preconditions.checkNotNull(specification);

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path(FeedController.PATH_ROOT + FeedController.PATH_PKG_LEAF);

        if(null!=specification.getNaturalLanguageCode()) {
            builder.queryParam(FeedController.KEY_NATURALLANGUAGECODE, specification.getNaturalLanguageCode());
        }

        if(null!=specification.getLimit()) {
            builder.queryParam(FeedController.KEY_LIMIT, specification.getLimit().toString());
        }

        if(null!=specification.getSupplierTypes()) {
            builder.queryParam(
                    FeedController.KEY_TYPES,
                    String.join(
                        ",",
                        specification.getSupplierTypes().stream().map(FeedSpecification.SupplierType::name).collect(Collectors.toList())
                    )
            );
        }

        if(null!=specification.getPkgNames()) {
            // split on hyphens because hyphens are not allowed in package names
            builder.queryParam(FeedController.KEY_PKGNAMES, String.join("-",specification.getPkgNames()));
        }

        return builder.build().toString();
    }

}
