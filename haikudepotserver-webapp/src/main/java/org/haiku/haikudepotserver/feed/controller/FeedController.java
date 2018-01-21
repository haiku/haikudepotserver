/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.feed.controller;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.rometools.rome.feed.synd.*;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.feed.FeedServiceImpl;
import org.haiku.haikudepotserver.feed.model.FeedService;
import org.haiku.haikudepotserver.feed.model.FeedSpecification;
import org.haiku.haikudepotserver.feed.model.SyndEntrySupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>This controller produces an ATOM feed of the latest happenings </p>
 */

@Controller
@RequestMapping(FeedService.PATH_ROOT)
public class FeedController {

    protected static Logger LOGGER = LoggerFactory.getLogger(FeedController.class);

    private final static String FEED_TITLE = "Haiku Depot Server Feed";
    private final static String FEED_ICON_TITLE = "Haiku Depot Server Icon";

    private final static int DEFAULT_LIMIT = 50;
    private final static int MAX_LIMIT = 100;

    private final static long EXPIRY_CACHE_SECONDS = 60;

    private final List<SyndEntrySupplier> syndEntrySuppliers;
    private final MessageSource messageSource;
    private final String baseUrl;

    private final LoadingCache<FeedSpecification,SyndFeed> feedCache;

    public FeedController(
            List<SyndEntrySupplier> syndEntrySuppliers,
            MessageSource messageSource,
            @Value("${baseurl}") String baseUrl) {
        this.syndEntrySuppliers = Preconditions.checkNotNull(syndEntrySuppliers);
        this.messageSource = Preconditions.checkNotNull(messageSource);
        this.baseUrl = Preconditions.checkNotNull(baseUrl);

        feedCache = CacheBuilder
                .newBuilder()
                .maximumSize(10)
                .expireAfterWrite(EXPIRY_CACHE_SECONDS, TimeUnit.SECONDS)
                .build(new CacheLoader<FeedSpecification, SyndFeed>() {
                    @Override
                    public SyndFeed load(FeedSpecification key) {
                        return createFeed(key);
                    }
                });
    }

    private SyndFeed createFeed(FeedSpecification key) {
        Preconditions.checkNotNull(key);
        Locale locale = Locale.ENGLISH;

        if (!Strings.isNullOrEmpty(key.getNaturalLanguageCode())) {
            locale = new Locale(key.getNaturalLanguageCode());
        }

        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType(key.getFeedType().getFeedType());
        feed.setTitle(FEED_TITLE);
        feed.setDescription(messageSource.getMessage("feed.description", new Object[] {}, locale));
        feed.setLink(baseUrl);
        feed.setPublishedDate(new java.util.Date());

        SyndImage image = new SyndImageImpl();
        image.setUrl(baseUrl + "/__img/haikudepot32.png");
        image.setTitle(FEED_ICON_TITLE);
        feed.setImage(image);

        List<SyndEntry> entries = new ArrayList<>();

        for(SyndEntrySupplier supplier : syndEntrySuppliers) {
            entries.addAll(supplier.generate(key));
        }

        // sort the entries and then take the first number of them up to the limit.

        entries.sort((o1, o2) -> -1 * o1.getPublishedDate().compareTo(o2.getPublishedDate()));

        if(entries.size() > key.getLimit()) {
            entries = entries.subList(0,key.getLimit());
        }

        feed.setEntries(entries);

        return feed;
    }

    @RequestMapping(
            value = "{pathBase:pkg\\.}{" + FeedServiceImpl.KEY_EXTENSION + ":atom|rss}",
            method = RequestMethod.GET)
    public void generate(
            HttpServletResponse response,
            @PathVariable(value = FeedServiceImpl.KEY_EXTENSION) String extension,
            @RequestParam(value = FeedServiceImpl.KEY_NATURALLANGUAGECODE, required = false) String naturalLanguageCode,
            @RequestParam(value = FeedServiceImpl.KEY_PKGNAMES, required = false) String pkgNames,
            @RequestParam(value = FeedServiceImpl.KEY_LIMIT, required = false) Integer limit,
            @RequestParam(value = FeedServiceImpl.KEY_TYPES, required = false) String types) throws IOException, FeedException {

        Preconditions.checkNotNull(response);

        if(null==limit || limit > MAX_LIMIT) {
            limit = DEFAULT_LIMIT;
        }

        FeedSpecification.FeedType feedType = tryDeriveFeedTypeByExtension(extension)
                .orElseThrow(() -> new IllegalStateException("unable to derive the feed type from [" + extension + "]"));

        FeedSpecification specification = new FeedSpecification();
        specification.setFeedType(feedType);
        specification.setLimit(limit > MAX_LIMIT ? MAX_LIMIT : limit);
        specification.setNaturalLanguageCode(!Strings.isNullOrEmpty(naturalLanguageCode) ? naturalLanguageCode : NaturalLanguage.CODE_ENGLISH);

        if(Strings.isNullOrEmpty(types)) {
            specification.setSupplierTypes(ImmutableList.copyOf(FeedSpecification.SupplierType.values()));
        }
        else {
            specification.setSupplierTypes(
                    Splitter.on(',').trimResults().omitEmptyStrings().splitToList(types)
                    .stream()
                    .map(FeedSpecification.SupplierType::valueOf)
                    .collect(Collectors.toList())
            );
        }

        if(Strings.isNullOrEmpty(pkgNames)) {
            specification.setPkgNames(null);
        }
        else {
            // split on hyphens because hyphens are not allowed in package names
            specification.setPkgNames(Splitter.on('-').trimResults().omitEmptyStrings().splitToList(pkgNames));
        }

        SyndFeed feed = feedCache.getUnchecked(specification);

        response.setContentType(feedType.getContentType());

        Writer writer = response.getWriter();
        SyndFeedOutput syndFeedOutput = new SyndFeedOutput();
        syndFeedOutput.output(feed, writer);

        writer.close();
    }

    private Optional<FeedSpecification.FeedType> tryDeriveFeedTypeByExtension(String extension) {
        if (!Strings.isNullOrEmpty(extension)) {
            for (FeedSpecification.FeedType feedType : FeedSpecification.FeedType.values()) {
                if (extension.equals(feedType.getExtension())) {
                    return Optional.of(feedType);
                }
            }
        }

        return Optional.empty();
    }


}
