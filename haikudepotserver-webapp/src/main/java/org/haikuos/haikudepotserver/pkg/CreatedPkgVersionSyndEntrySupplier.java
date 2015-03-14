/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.net.MediaType;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndContentImpl;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndEntryImpl;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haikuos.haikudepotserver.dataobjects.NaturalLanguage;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.PkgVersion;
import org.haikuos.haikudepotserver.dataobjects.PkgVersionLocalization;
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

    @Override
    public List<SyndEntry> generate(final FeedSpecification specification) {
        Preconditions.checkNotNull(specification);

        if(specification.getSupplierTypes().contains(FeedSpecification.SupplierType.CREATEDPKGVERSION)) {

            if(null!=specification.getPkgNames() && specification.getPkgNames().isEmpty()) {
                return Collections.emptyList();
            }

            List<Expression> expressions = Lists.newArrayList();

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

            List<PkgVersion> pkgVersions = serverRuntime.getContext().performQuery(query);

            return Lists.transform(
                    pkgVersions,
                    new Function<PkgVersion, SyndEntry>() {
                        @Override
                        public SyndEntry apply(PkgVersion input) {

                            SyndEntry entry = new SyndEntryImpl();

                            entry.setPublishedDate(input.getCreateTimestamp());
                            entry.setUpdatedDate(input.getModifyTimestamp());
                            entry.setUri(URI_PREFIX +
                                    Hashing.sha1().hashUnencodedChars(
                                            String.format(
                                                    "%s_::_%s_::_%s",
                                                    this.getClass().getCanonicalName(),
                                                    input.getPkg().getName(),
                                                    input.toVersionCoordinates().toString())).toString());

                            // see method for this on "PkgVersion.toCoordinate().pathCom...()"

                            entry.setLink(String.format(
                                    "%s/#/pkg/%s/%s/%s/%s/%s/%d/%s",
                                    baseUrl,
                                    input.getPkg().getName(),
                                    input.getMajor(),
                                    null == input.getMinor() ? "-" : input.getMinor(),
                                    null == input.getMicro() ? "-" : input.getMicro(),
                                    null == input.getPreRelease() ? "-" : input.getPreRelease(),
                                    null == input.getRevision() ? "-" : input.getRevision(),
                                    input.getArchitecture().getCode()));

                            entry.setTitle(messageSource.getMessage(
                                    "feed.createdPkgVersion.atom.title",
                                    new Object[] { input.toStringWithPkgAndArchitecture() },
                                    new Locale(specification.getNaturalLanguageCode())
                            ));

                            {
                                Optional<PkgVersionLocalization> pkgVersionLocalizationOptional = input.getPkgVersionLocalization(specification.getNaturalLanguageCode());

                                if(!pkgVersionLocalizationOptional.isPresent()) {
                                    pkgVersionLocalizationOptional = input.getPkgVersionLocalization(NaturalLanguage.CODE_ENGLISH);
                                }

                                SyndContent content = new SyndContentImpl();
                                content.setType(MediaType.PLAIN_TEXT_UTF_8.type());
                                content.setValue(pkgVersionLocalizationOptional.get().getSummary());
                                entry.setDescription(content);
                            }

                            return entry;
                        }
                    }
            );

        }

        return Collections.emptyList();
    }

}
