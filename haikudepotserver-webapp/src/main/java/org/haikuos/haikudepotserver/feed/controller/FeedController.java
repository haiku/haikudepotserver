/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.feed.controller;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.net.MediaType;
import com.sun.syndication.feed.synd.*;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedOutput;
import org.haikuos.haikudepotserver.dataobjects.NaturalLanguage;
import org.haikuos.haikudepotserver.feed.model.FeedSpecification;
import org.haikuos.haikudepotserver.feed.model.SyndEntrySupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>This controller produces an ATOM feed of the latest happenings </p>
 */

@Controller
@RequestMapping(FeedController.PATH_ROOT)
public class FeedController {

    protected static Logger LOGGER = LoggerFactory.getLogger(FeedController.class);

    public final static String KEY_NATURALLANGUAGECODE = "natlangcode";
    public final static String KEY_PKGNAMES = "pkgnames";
    public final static String KEY_LIMIT = "limit";
    public final static String KEY_TYPES = "types";

    public final static String FEEDTYPE = "atom_1.0";
    public final static String FEEDTITLE = "Haiku Depot Server Feed";

    public final static int DEFAULT_LIMIT = 50;
    public final static int MAX_LIMIT = 100;

    public final static long EXPIRY_CACHE_SECONDS = 60;

    public final static String PATH_ROOT = "/feed";
    public final static String PATH_PKG_LEAF = "/pkg.atom";

    @Resource
    private List<SyndEntrySupplier> syndEntrySuppliers;

    @Value("${baseurl}")
    private String baseUrl;

    private LoadingCache<FeedSpecification,SyndFeed> feedCache = CacheBuilder
            .newBuilder()
            .maximumSize(10)
            .expireAfterWrite(EXPIRY_CACHE_SECONDS, TimeUnit.SECONDS)
            .build(new CacheLoader<FeedSpecification, SyndFeed>() {
                @Override
                public SyndFeed load(FeedSpecification key) throws Exception {
                    Preconditions.checkNotNull(key);

                    SyndFeed feed = new SyndFeedImpl();
                    feed.setFeedType(FEEDTYPE);
                    feed.setTitle(FEEDTITLE);
                    feed.setLink(baseUrl);
                    feed.setPublishedDate(new java.util.Date());

                    SyndImage image = new SyndImageImpl();
                    image.setUrl(baseUrl + "/img/haikudepot32.png");
                    feed.setImage(image);

                    List<SyndEntry> entries = Lists.newArrayList();

                    for(SyndEntrySupplier supplier : syndEntrySuppliers) {
                        entries.addAll(supplier.generate(key));
                    }

                    // sort the entries and then take the first number of them up to the limit.

                    Collections.sort(entries, new Comparator<SyndEntry>() {
                        @Override
                        public int compare(SyndEntry o1, SyndEntry o2) {
                            return -1 * o1.getPublishedDate().compareTo(o2.getPublishedDate());
                        }
                    });

                    if(entries.size() > key.getLimit()) {
                        entries = entries.subList(0,key.getLimit());
                    }

                    feed.setEntries(entries);

                    return feed;
                }
            });

    @RequestMapping(value = PATH_PKG_LEAF, method = RequestMethod.GET)
    public void generate(
            HttpServletResponse response,
            @RequestParam(value = KEY_NATURALLANGUAGECODE, required = false) String naturalLanguageCode,
            @RequestParam(value = KEY_PKGNAMES, required = false) String pkgNames,
            @RequestParam(value = KEY_LIMIT, required = false) Integer limit,
            @RequestParam(value = KEY_TYPES, required = false) String types) throws IOException, FeedException {

        Preconditions.checkNotNull(response);

        if(null==limit || limit > MAX_LIMIT) {
            limit = DEFAULT_LIMIT;
        }

        FeedSpecification specification = new FeedSpecification();
        specification.setLimit(limit > MAX_LIMIT ? MAX_LIMIT : limit);
        specification.setNaturalLanguageCode(!Strings.isNullOrEmpty(naturalLanguageCode) ? naturalLanguageCode : NaturalLanguage.CODE_ENGLISH);

        if(Strings.isNullOrEmpty(types)) {
            specification.setSupplierTypes(ImmutableList.copyOf(FeedSpecification.SupplierType.values()));
        }
        else {
            specification.setSupplierTypes(Lists.transform(
                    Splitter.on(',').trimResults().omitEmptyStrings().splitToList(types),
                    new Function<String, FeedSpecification.SupplierType>() {
                        @Override
                        public FeedSpecification.SupplierType apply(String input) {
                            return FeedSpecification.SupplierType.valueOf(input);
                        }
                    }
            ));
        }

        if(Strings.isNullOrEmpty(pkgNames)) {
            specification.setPkgNames(null);
        }
        else {
            // split on hyphens because hyphens are not allowed in package names
            specification.setPkgNames(Splitter.on('-').trimResults().omitEmptyStrings().splitToList(pkgNames));
        }

        SyndFeed feed = feedCache.getUnchecked(specification);

        response.setContentType(MediaType.ATOM_UTF_8.toString());

        Writer writer = response.getWriter();
        SyndFeedOutput syndFeedOutput = new SyndFeedOutput();
        syndFeedOutput.output(feed, writer);

        writer.close();
    }


}
