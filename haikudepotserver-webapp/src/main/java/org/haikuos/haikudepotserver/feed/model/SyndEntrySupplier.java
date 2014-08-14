/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.feed.model;

import com.sun.syndication.feed.synd.SyndEntry;

import java.util.List;

/**
 * <p>Implementers of this interface are able to produce sync entry objects that are able to
 * form part of an RSS/Atom feed.</p>
 */

public interface SyndEntrySupplier {

    public final static String URI_PREFIX = "urn:hdsfeedentry:";

    List<SyndEntry> generate(FeedSpecification specification);

}
