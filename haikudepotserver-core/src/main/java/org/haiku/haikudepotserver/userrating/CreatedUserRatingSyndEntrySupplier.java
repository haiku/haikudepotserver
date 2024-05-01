/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.userrating;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import com.google.common.net.MediaType;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.dataobjects.UserRating;
import org.haiku.haikudepotserver.feed.model.FeedSpecification;
import org.haiku.haikudepotserver.feed.model.SyndEntrySupplier;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationService;
import org.haiku.haikudepotserver.pkg.model.ResolvedPkgVersionLocalization;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>This class produces RSS feed entries related to user ratings.</p>
 */

@Component
public class CreatedUserRatingSyndEntrySupplier implements SyndEntrySupplier {

    private final static int CONTENT_LENGTH = 80;

    private final static char STAR_FILLED = '*'; // '\u2605';
    private final static char STAR_HOLLOW = '.'; // '\u2606';

    private final ServerRuntime serverRuntime;
    private final String baseUrl;
    private final MessageSource messageSource;
    private final PkgLocalizationService pkgLocalizationService;

    public CreatedUserRatingSyndEntrySupplier(
            ServerRuntime serverRuntime,
            @Value("${hds.base-url}") String baseUrl,
            MessageSource messageSource,
            PkgLocalizationService pkgLocalizationService
    ) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.baseUrl = Preconditions.checkNotNull(baseUrl);
        this.messageSource = Preconditions.checkNotNull(messageSource);
        this.pkgLocalizationService = Preconditions.checkNotNull(pkgLocalizationService);
    }

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

            ObjectSelect<UserRating> objectSelect = ObjectSelect.query(UserRating.class)
                    .where(UserRating.ACTIVE.isTrue())
                    .and(UserRating.PKG_VERSION.dot(PkgVersion.ACTIVE).isTrue())
                    .and(UserRating.PKG_VERSION.dot(PkgVersion.PKG).dot(Pkg.ACTIVE).isTrue())
                    .statementFetchSize(specification.getLimit())
                    .orderBy(UserRating.CREATE_TIMESTAMP.desc());

            if(null!=specification.getPkgNames()) {
                objectSelect.and(
                        UserRating.PKG_VERSION.dot(PkgVersion.PKG).dot(Pkg.NAME).in(specification.getPkgNames()));
            }

            ObjectContext context = serverRuntime.newContext();
            List<UserRating> userRatings = objectSelect.select(serverRuntime.newContext());
            NaturalLanguage naturalLanguage = deriveNaturalLanguage(context, specification);

            return userRatings
                    .stream()
                    .map(ur -> {
                        SyndEntry entry = new SyndEntryImpl();
                        entry.setPublishedDate(ur.getCreateTimestamp());
                        entry.setUpdatedDate(ur.getModifyTimestamp());
                        entry.setAuthor(ur.getUser().getNickname());
                        entry.setUri(URI_PREFIX +
                                Hashing.sha1().hashUnencodedChars(
                                        String.format(
                                                "%s_::_%s_::_%s_::_%s",
                                                this.getClass().getCanonicalName(),
                                                ur.getPkgVersion().getPkg().getName(),
                                                ur.getPkgVersion().toVersionCoordinates().toString(),
                                                ur.getUser().getNickname())));
                        entry.setLink(String.format(
                                "%s/#!/userrating/%s",
                                baseUrl,
                                ur.getCode()));

                        ResolvedPkgVersionLocalization resolvedPkgVersionLocalization =
                                pkgLocalizationService.resolvePkgVersionLocalization(context, ur.getPkgVersion(), null, naturalLanguage);

                        entry.setTitle(messageSource.getMessage(
                                "feed.createdUserRating.atom.title",
                                new Object[]{
                                        Optional.ofNullable(resolvedPkgVersionLocalization.getTitle())
                                                .orElse(ur.getPkgVersion().getPkg().getName()),
                                        ur.getUser().getNickname()
                                },
                                specification.getNaturalLanguageCoordinates().toLocale()
                        ));

                        String contentString = ur.getComment();

                        if (null != contentString && contentString.length() > CONTENT_LENGTH) {
                            contentString = contentString.substring(0, CONTENT_LENGTH) + "...";
                        }

                        // if there is a rating then express this as a string using unicode
                        // characters.

                        if (null != ur.getRating()) {
                            contentString = buildRatingIndicator(ur.getRating()) +
                                    (Strings.isNullOrEmpty(contentString) ? "" : " -- " + contentString);
                        }

                        SyndContentImpl content = new SyndContentImpl();
                        content.setType(MediaType.PLAIN_TEXT_UTF_8.type());
                        content.setValue(contentString);
                        entry.setDescription(content);

                        return entry;
                    })
                    .collect(Collectors.toList());

        }

        return Collections.emptyList();
    }

    private NaturalLanguage deriveNaturalLanguage(ObjectContext context, final FeedSpecification specification) {
        return Optional.ofNullable(specification.getNaturalLanguageCoordinates())
                .map(c -> NaturalLanguage.getByNaturalLanguage(context, c))
                .orElse(NaturalLanguage.getEnglish(context));
    }

}
