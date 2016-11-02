/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.feed.model;

import com.rometools.rome.feed.synd.SyndEntry;

import java.util.List;

/**
 * <p>Implementers of this interface are able to produce sync entry objects that are able to
 * form part of an RSS/Atom feed.</p>
 */

public interface SyndEntrySupplier {

    String URI_PREFIX = "urn:hdsfeedentry:";

    List<SyndEntry> generate(FeedSpecification specification);

}
