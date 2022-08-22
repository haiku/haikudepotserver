/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.thymeleaf;

import com.google.common.base.Preconditions;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationLookupService;
import org.haiku.haikudepotserver.pkg.model.ResolvedPkgVersionLocalization;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.IAttribute;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.standard.expression.IStandardExpression;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressions;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.Optional;

/**
 * <p>This will render a package version's title in a Thymeleaf template.</p>
 */

public class PkgVersionTitleContentProcessor extends AbstractAttributeTagProcessor {

    private final ServerRuntime serverRuntime;

    private final PkgLocalizationLookupService pkgLocalizationLookupService;

    public PkgVersionTitleContentProcessor(
            final String dialectPrefix,
            ServerRuntime serverRuntime,
            PkgLocalizationLookupService pkgLocalizationLookupService) {
        super(
                TemplateMode.HTML,
                dialectPrefix,
                null,
                true,
                "pkgversiontitle",
                true,
                200,
                true);
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.pkgLocalizationLookupService = Preconditions.checkNotNull(pkgLocalizationLookupService);
    }

    @Override
    protected void doProcess(ITemplateContext context, IProcessableElementTag tag, AttributeName attributeName, String attributeValue, IElementTagStructureHandler structureHandler) {
        PkgVersion pkgVersion = derivePkgVersion(context, attributeValue);

        if (null == pkgVersion) {
            throw new IllegalStateException("the pkg-version was unable to be derived");
        }

        ResolvedPkgVersionLocalization localization = pkgLocalizationLookupService.resolvePkgVersionLocalization(
                serverRuntime.newContext(),
                derivePkgVersion(context, attributeValue),
                null,
                deriveNaturalLanguage(context, tag));

        String title = Optional.ofNullable(localization.getTitle()).orElseGet(() -> pkgVersion.getPkg().getName());

        structureHandler.setBody(
                org.thymeleaf.util.StringUtils.escapeXml(title),
                false);
    }

    private NaturalLanguage deriveNaturalLanguage(
            final ITemplateContext context,
            IProcessableElementTag tag) {
        return Optional.ofNullable(tag.getAttribute("naturallanguage"))
                .map(IAttribute::getValue)
                .map(v -> {
                    final IStandardExpressionParser parser = StandardExpressions.getExpressionParser(context.getConfiguration());
                    final IStandardExpression expression = parser.parseExpression(context, v);
                    return (NaturalLanguage) expression.execute(context);
                })
                .orElseThrow(() -> new IllegalStateException("missing natural language"));
    }

    private PkgVersion derivePkgVersion(
            final ITemplateContext context,
            final String attributeValue) {
        Preconditions.checkArgument(StringUtils.isNotBlank(attributeValue), "the attribute value is required");
        final IStandardExpressionParser parser = StandardExpressions.getExpressionParser(context.getConfiguration());
        final IStandardExpression expression = parser.parseExpression(context, attributeValue);
        return (PkgVersion) expression.execute(context);
    }

}
