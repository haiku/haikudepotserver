/*
 * Copyright 2022-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.thymeleaf;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.expression.IExpressionObjects;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.standard.expression.IStandardExpression;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressions;
import org.thymeleaf.templatemode.TemplateMode;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * <p>This will render an anchor <code>href</code> to view a package
 * version in a Thymeleaf template's anchor tag.</p>
 */

public class PkgVersionLinkContentProcessor extends AbstractAttributeTagProcessor {

    public PkgVersionLinkContentProcessor(final String dialectPrefix) {
        super(
                TemplateMode.HTML,
                dialectPrefix,
                null,
                true,
                "pkgversionlink",
                true,
                200,
                true);
    }

    @Override
    protected void doProcess(ITemplateContext context, IProcessableElementTag tag, AttributeName attributeName, String attributeValue, IElementTagStructureHandler structureHandler) {
        PkgVersion pkgVersion = derivePkgVersion(context, attributeValue);

        if (null == pkgVersion) {
            throw new IllegalStateException("the pkg-version was unable to be derived");
        }

        structureHandler.setAttribute("href", toUriComponents(context, pkgVersion).toUriString());
        structureHandler.setAttribute("alt", pkgVersion.getPkg().getName());
    }

    private UriComponents toUriComponents(ITemplateContext context, PkgVersion pkgVersion) {
        HttpServletRequest request = (HttpServletRequest) context.getVariable("request");

        return UriComponentsBuilder.newInstance()
                .path(MultipageConstants.PATH_MULTIPAGE)
                .pathSegment("pkg")
                .pathSegment(pkgVersion.getPkg().getName())
                .pathSegment(pkgVersion.getRepositorySource().getRepository().getCode())
                .pathSegment(pkgVersion.getRepositorySource().getCode())
                .pathSegment(emptyToHyphen(pkgVersion.getMajor()))
                .pathSegment(emptyToHyphen(pkgVersion.getMinor()))
                .pathSegment(emptyToHyphen(pkgVersion.getMicro()))
                .pathSegment(emptyToHyphen(pkgVersion.getPreRelease()))
                .pathSegment(Optional.ofNullable(pkgVersion.getRevision()).map(Object::toString).orElse("-"))
                .pathSegment(pkgVersion.getArchitecture().getCode())
                .queryParam(
                        WebConstants.KEY_NATURALLANGUAGECODE,
                        request.getParameter(WebConstants.KEY_NATURALLANGUAGECODE))
                .build();
    }

    private static String emptyToHyphen(String part) {
        return Optional.ofNullable(part)
                .filter(StringUtils::isNotBlank)
                .orElse("-");
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
