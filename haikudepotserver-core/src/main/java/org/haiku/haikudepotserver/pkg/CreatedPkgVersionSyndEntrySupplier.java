/*
 * Copyright 2014-2015, Andrew Lindesay
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
import com.rometools.utils.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.dataobjects.PkgVersionLocalization;
import org.haiku.haikudepotserver.feed.model.FeedSpecification;
import org.haiku.haikudepotserver.feed.model.SyndEntrySupplier;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationService;
import org.haiku.haikudepotserver.pkg.model.ResolvedPkgVersionLocalization;
import org.haiku.haikudepotserver.support.cayenne.ExpressionHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>This class produces RSS feed entries related to new pkg versions.</p>
 */

@Component
public class CreatedPkgVersionSyndEntrySupplier implements SyndEntrySupplier {

    @Resource
    private ServerRuntime serverRuntime;

    @Value("${baseurl}")
    private String baseUrl;

    @Resource
    private MessageSource messageSource;

    @Resource
    private PkgLocalizationService pkgLocalizationService;

    @Override
    public List<SyndEntry> generate(final FeedSpecification specification) {
        Preconditions.checkNotNull(specification);

        if(specification.getSupplierTypes().contains(FeedSpecification.SupplierType.CREATEDPKGVERSION)) {

            if(null!=specification.getPkgNames() && specification.getPkgNames().isEmpty()) {
                return Collections.emptyList();
            }

            List<Expression> expressions = new ArrayList<>();

            if(null!=specification.getPkgNames()) {
                expressions.add(ExpressionFactory.inExp(
                                PkgVersion.PKG_PROPERTY + "." + Pkg.NAME_PROPERTY,
                                specification.getPkgNames())
                );
            }

            expressions.add(ExpressionFactory.matchExp(
                            PkgVersion.ACTIVE_PROPERTY,
                            Boolean.TRUE)
            );

            expressions.add(ExpressionFactory.matchExp(
                            PkgVersion.PKG_PROPERTY + "." + Pkg.ACTIVE_PROPERTY,
                            Boolean.TRUE)
            );

            SelectQuery query = new SelectQuery(
                    PkgVersion.class,
                    ExpressionHelper.andAll(expressions));

            query.addOrdering(new Ordering(
                    PkgVersion.CREATE_TIMESTAMP_PROPERTY,
                    SortOrder.DESCENDING));

            query.setFetchLimit(specification.getLimit());

            ObjectContext context = serverRuntime.getContext();
            NaturalLanguage naturalLanguage = Strings.isBlank(specification.getNaturalLanguageCode())
                ? NaturalLanguage.getEnglish(context)
                    : NaturalLanguage.getByCode(context, specification.getNaturalLanguageCode())
                    .orElseThrow(() -> new IllegalStateException("unable to find natural language; "
                            + specification.getNaturalLanguageCode()));
            List<PkgVersion> pkgVersions = context.performQuery(query);

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
                            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl).pathSegment("#", "pkg");
                            pv.appendPathSegments(builder);
                            entry.setLink(builder.build().toUriString());
                        }

                        entry.setTitle(messageSource.getMessage(
                                "feed.createdPkgVersion.atom.title",
                                new Object[]{pv.toStringWithPkgAndArchitecture()},
                                new Locale(specification.getNaturalLanguageCode())
                        ));

                        {
                            ResolvedPkgVersionLocalization resolvedPkgVersionLocalization = pkgLocalizationService
                                    .resolvePkgVersionLocalization(context, pv, null, naturalLanguage);

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

}
