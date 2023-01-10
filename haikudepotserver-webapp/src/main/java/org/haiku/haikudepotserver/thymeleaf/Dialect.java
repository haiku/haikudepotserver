/*
 * Copyright 2022-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.thymeleaf;

import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.multipage.thymeleaf.NaturalLanguageChooserContentProcessor;
import org.haiku.haikudepotserver.multipage.thymeleaf.PaginationLinksContentProcessor;
import org.haiku.haikudepotserver.multipage.thymeleaf.PkgIconContentProcessor;
import org.haiku.haikudepotserver.multipage.thymeleaf.PkgVersionLinkContentProcessor;
import org.haiku.haikudepotserver.multipage.thymeleaf.PkgVersionTitleContentProcessor;
import org.haiku.haikudepotserver.multipage.thymeleaf.PlainTextContentProcessor;
import org.haiku.haikudepotserver.multipage.thymeleaf.RatingIndicatorContentProcessor;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationLookupService;
import org.haiku.haikudepotserver.singlepage.thymeleaf.AppScriptsProcessor;
import org.springframework.core.io.Resource;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.dialect.IProcessorDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;
import org.thymeleaf.processor.IProcessor;

import java.util.Set;

/**
 * <p>This is a means of setting up special configuration for Thymeleaf templates
 * in the application.</p>
 */

public class Dialect implements IProcessorDialect, IExpressionObjectDialect {

    public final static String PREFIX = "hds";

    private final ServerRuntime serverRuntime;

    private final PkgLocalizationLookupService pkgLocalizationLookupService;

    private final boolean isProduction;

    private final Resource singlePageApplicationJavaScriptIndexResource;

    public Dialect(
            ServerRuntime serverRuntime,
            PkgLocalizationLookupService pkgLocalizationLookupService,
            Boolean isProduction,
            Resource singlePageApplicationJavaScriptIndexResource) {
        this.serverRuntime = serverRuntime;
        this.pkgLocalizationLookupService = pkgLocalizationLookupService;
        this.isProduction = isProduction;
        this.singlePageApplicationJavaScriptIndexResource = singlePageApplicationJavaScriptIndexResource;
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public String getName() {
        return "HaikuDepotServer";
    }

    @Override
    public int getDialectProcessorPrecedence() {
        return 2000;
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        return Set.of(
                new IncludePassiveContentProcessor(dialectPrefix),
                new PaginationLinksContentProcessor(dialectPrefix),
                new NaturalLanguageChooserContentProcessor(dialectPrefix),
                new PkgIconContentProcessor(dialectPrefix),
                new PkgVersionLinkContentProcessor(dialectPrefix),
                new PkgVersionTitleContentProcessor(
                        dialectPrefix, serverRuntime, pkgLocalizationLookupService),
                new RatingIndicatorContentProcessor(dialectPrefix),
                new PlainTextContentProcessor(dialectPrefix),
                new AppScriptsProcessor(dialectPrefix, isProduction, singlePageApplicationJavaScriptIndexResource));
    }

    @Override
    public IExpressionObjectFactory getExpressionObjectFactory() {
        return new ExpressionObjectFactory();
    }
}
