/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.common.net.MediaType;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.feed.model.FeedSpecification;
import org.haiku.haikudepotserver.feed.model.SyndEntrySupplier;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationService;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.pkg.model.ResolvedPkgVersionLocalization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>This class produces RSS feed entries related to new pkg versions.</p>
 */

@Component
public class CreatedPkgVersionSyndEntrySupplier implements SyndEntrySupplier {

    protected static Logger LOGGER = LoggerFactory.getLogger(CreatedPkgVersionSyndEntrySupplier.class);

    private final ServerRuntime serverRuntime;
    private final String baseUrl;
    private final MessageSource messageSource;
    private final PkgLocalizationService pkgLocalizationService;

    public CreatedPkgVersionSyndEntrySupplier(
            ServerRuntime serverRuntime,
            @Value("${hds.base-url}") String baseUrl,
            MessageSource messageSource,
            PkgLocalizationService pkgLocalizationService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.baseUrl = Preconditions.checkNotNull(baseUrl);
        this.messageSource = Preconditions.checkNotNull(messageSource);
        this.pkgLocalizationService = Preconditions.checkNotNull(pkgLocalizationService);
    }

    @Override
    public List<SyndEntry> generate(final FeedSpecification specification) {
        Preconditions.checkNotNull(specification);

        if(specification.getSupplierTypes().contains(FeedSpecification.SupplierType.CREATEDPKGVERSION)) {

            if(null!=specification.getPkgNames() && specification.getPkgNames().isEmpty()) {
                return Collections.emptyList();
            }

            ObjectSelect<PkgVersion> objectSelect = ObjectSelect
                    .query(PkgVersion.class)
                    .where(PkgVersion.ACTIVE.isTrue())
                    .and(PkgVersion.PKG.dot(Pkg.ACTIVE).isTrue())
                    .and(ExpressionFactory.or(
                         PkgVersion.PKG.dot(Pkg.NAME).endsWith(PkgService.SUFFIX_PKG_DEBUGINFO),
                         PkgVersion.PKG.dot(Pkg.NAME).endsWith(PkgService.SUFFIX_PKG_DEVELOPMENT),
                         PkgVersion.PKG.dot(Pkg.NAME).endsWith(PkgService.SUFFIX_PKG_SOURCE)
                    ).notExp())
                    .orderBy(PkgVersion.CREATE_TIMESTAMP.desc())
                    .limit(specification.getLimit());

            if(null!=specification.getPkgNames()) {
                objectSelect.and(PkgVersion.PKG.dot(Pkg.NAME).in(specification.getPkgNames()));
            }

            ObjectContext context = serverRuntime.newContext();
            NaturalLanguage naturalLanguage = deriveNaturalLanguage(context, specification);
            List<PkgVersion> pkgVersions = objectSelect.select(context);

            return pkgVersions
                    .stream()
                    .map(pv -> {
                        SyndEntry entry = new SyndEntryImpl();

                        entry.setPublishedDate(pv.getCreateTimestamp());
                        entry.setUpdatedDate(pv.getModifyTimestamp());
                        entry.setUri(URI_PREFIX +
                                Hashing.sha1().hashUnencodedChars(
                                        String.format(
                                                "%s_::_%s_::_%s",
                                                this.getClass().getCanonicalName(),
                                                pv.getPkg().getName(),
                                                pv.toVersionCoordinates().toString())).toString());

                        {
                            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl).pathSegment("#!", "pkg");
                            pv.appendPathSegments(builder);
                            entry.setLink(builder.build().toUriString());
                        }

                        ResolvedPkgVersionLocalization resolvedPkgVersionLocalization =
                                pkgLocalizationService.resolvePkgVersionLocalization(context, pv, null, naturalLanguage);

                        entry.setTitle(messageSource.getMessage(
                                "feed.createdPkgVersion.atom.title",
                                new Object[] {
                                        Optional.ofNullable(resolvedPkgVersionLocalization.getTitle()).orElse(pv.getPkg().getName()),
                                        pv.toVersionCoordinates().toString()
                                },
                                new Locale(naturalLanguage.getCode())
                        ));

                        {
                            SyndContent content = new SyndContentImpl();
                            content.setType(MediaType.PLAIN_TEXT_UTF_8.type());
                            content.setValue(resolvedPkgVersionLocalization.getSummary());
                            entry.setDescription(content);
                        }

                        return entry;
                    })
                    .collect(Collectors.toList());

        }

        return Collections.emptyList();
    }

    private NaturalLanguage deriveNaturalLanguage(ObjectContext context, final FeedSpecification specification) {
        return Optional.ofNullable(specification.getNaturalLanguageCoordinates())
                .map(coordinates -> NaturalLanguage.getByNaturalLanguage(context, coordinates))
                .orElse(NaturalLanguage.getEnglish(context));
    }

}
