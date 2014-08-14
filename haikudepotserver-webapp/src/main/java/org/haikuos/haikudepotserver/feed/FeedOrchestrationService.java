/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.feed;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import org.haikuos.haikudepotserver.feed.controller.FeedController;
import org.haikuos.haikudepotserver.feed.model.FeedSpecification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class FeedOrchestrationService {

    @Value("${baseurl}")
    String baseUrl;

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
            builder.queryParam(FeedController.KEY_TYPES, Joiner.on(',').join(Iterables.transform(
                    specification.getSupplierTypes(),
                    new Function<FeedSpecification.SupplierType, Object>() {
                        @Override
                        public Object apply(FeedSpecification.SupplierType input) {
                            return input.name();
                        }
                    }
            )));
        }

        if(null!=specification.getPkgNames()) {
            // split on hyphens because hyphens are not allowed in package names
            builder.queryParam(FeedController.KEY_PKGNAMES, Joiner.on('-').join(specification.getPkgNames()));
        }

        return builder.build().toString();
    }

}
