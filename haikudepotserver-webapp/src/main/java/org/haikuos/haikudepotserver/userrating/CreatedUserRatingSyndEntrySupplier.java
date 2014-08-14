/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.net.MediaType;
import com.sun.syndication.feed.synd.SyndContentImpl;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndEntryImpl;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.PkgVersion;
import org.haikuos.haikudepotserver.dataobjects.UserRating;
import org.haikuos.haikudepotserver.support.cayenne.ExpressionHelper;
import org.haikuos.haikudepotserver.feed.model.FeedSpecification;
import org.haikuos.haikudepotserver.feed.model.SyndEntrySupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * <p>This class produces RSS feed entries related to user ratings.</p>
 */

@Component
public class CreatedUserRatingSyndEntrySupplier implements SyndEntrySupplier {

    private final static int CONTENT_LENGTH = 80;

    private final char STAR_FILLED = '*'; // '\u2605';
    private final char STAR_HOLLOW = '.'; // '\u2606';

    @Resource
    ServerRuntime serverRuntime;

    @Value("${baseurl}")
    String baseUrl;

    @Resource
    MessageSource messageSource;

    /**
     * <p>Produces a string containing a list of stars; either hollow or filled to indicate the user rating from
     * zero to five.  The stars are made from unicode chars.</p>
     */

    private String buildRatingIndicator(int rating) {
        StringBuilder buffer = new StringBuilder();
        buildRatingIndicator(buffer, rating);
        return buffer.toString();
    }

    /**
     * <p>This is a recursive function to build the list of stars for the rating.</p>
     */

    private StringBuilder buildRatingIndicator(StringBuilder buffer, int rating) {
        Preconditions.checkNotNull(buffer);
        Preconditions.checkState(rating >= 0 && rating <= 5);

        if(buffer.length() < (rating==5 ? 9 : rating*2)) {
            buffer.append(STAR_FILLED);
        }
        else {
            buffer.append(STAR_HOLLOW);
        }

        if(9 != buffer.length()) {
            buffer.append(" ");
        }
        else {
            return buffer;
        }

        return buildRatingIndicator(buffer,rating);
    }

    @Override
    public List<SyndEntry> generate(final FeedSpecification specification) {
        Preconditions.checkNotNull(specification);

        if(specification.getSupplierTypes().contains(FeedSpecification.SupplierType.CREATEDUSERRATING)) {

            if(null!=specification.getPkgNames() && specification.getPkgNames().isEmpty()) {
                return Collections.emptyList();
            }

            List<Expression> expressions = Lists.newArrayList();

            if(null!=specification.getPkgNames()) {
                expressions.add(ExpressionFactory.inExp(
                                UserRating.PKG_VERSION_PROPERTY + "." + PkgVersion.PKG_PROPERTY + "." + Pkg.NAME_PROPERTY,
                                specification.getPkgNames())
                );
            }

            expressions.add(ExpressionFactory.matchExp(
                            UserRating.ACTIVE_PROPERTY,
                            Boolean.TRUE)
            );

            expressions.add(ExpressionFactory.matchExp(
                            UserRating.PKG_VERSION_PROPERTY + "." + PkgVersion.ACTIVE_PROPERTY,
                            Boolean.TRUE)
            );

            expressions.add(ExpressionFactory.matchExp(
                            UserRating.PKG_VERSION_PROPERTY + "." + PkgVersion.PKG_PROPERTY + "." + Pkg.ACTIVE_PROPERTY,
                            Boolean.TRUE)
            );

            SelectQuery query = new SelectQuery(
                    UserRating.class,
                    ExpressionHelper.andAll(expressions));

            query.addOrdering(new Ordering(
                    UserRating.CREATE_TIMESTAMP_PROPERTY,
                    SortOrder.DESCENDING));

            query.setFetchLimit(specification.getLimit());

            List<UserRating> userRatings = serverRuntime.getContext().performQuery(query);

            return Lists.transform(
                    userRatings,
                    new Function<UserRating, SyndEntry>() {
                        @Override
                        public SyndEntry apply(UserRating input) {

                            SyndEntry entry = new SyndEntryImpl();
                            entry.setPublishedDate(input.getCreateTimestamp());
                            entry.setUpdatedDate(input.getModifyTimestamp());
                            entry.setAuthor(input.getUser().getNickname());
                            entry.setUri(URI_PREFIX +
                                    Hashing.sha1().hashUnencodedChars(
                                            String.format(
                                                    "%s_::_%s_::_%s_::_%s",
                                                    this.getClass().getCanonicalName(),
                                                    input.getPkgVersion().getPkg().getName(),
                                                    input.getPkgVersion().toVersionCoordinates().toString(),
                                                    input.getUser().getNickname())).toString());
                            entry.setLink(String.format(
                                    "%s/#/userrating/%s",
                                    baseUrl,
                                    input.getCode()));

                            entry.setTitle(messageSource.getMessage(
                                    "feed.createdUserRating.atom.title",
                                    new Object[]{
                                            input.getPkgVersion().toStringWithPkgAndArchitecture(),
                                            input.getUser().getNickname()
                                    },
                                    new Locale(specification.getNaturalLanguageCode())
                            ));

                            String contentString = input.getComment();

                            if(null!=contentString && contentString.length() > CONTENT_LENGTH) {
                                contentString = contentString.substring(0,CONTENT_LENGTH) + "...";
                            }

                            // if there is a rating then express this as a string using unicode
                            // characters.

                            if(null!=input.getRating()) {
                                contentString = buildRatingIndicator(input.getRating()) +
                                        (Strings.isNullOrEmpty(contentString) ? "" : " -- " + contentString);
                            }

                            SyndContentImpl content = new SyndContentImpl();
                            content.setType(MediaType.PLAIN_TEXT_UTF_8.type());
                            content.setValue(contentString);
                            entry.setDescription(content);

                            return entry;
                        }
                    }
            );

        }

        return Collections.emptyList();
    }
}
