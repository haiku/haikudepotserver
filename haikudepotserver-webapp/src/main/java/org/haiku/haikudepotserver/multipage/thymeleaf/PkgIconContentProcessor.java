/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.thymeleaf;

import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.pkg.controller.PkgIconController;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IAttribute;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.standard.expression.IStandardExpression;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressions;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.Optional;

/**
 * <p>This will be able to render a packages' icon in a Thymeleaf
 * template.</p>
 */

public class PkgIconContentProcessor extends AbstractElementTagProcessor {

    private final static int DEFAULT_SIZE = 16;

    public PkgIconContentProcessor(final String dialectPrefix) {
        super(
                TemplateMode.HTML,
                dialectPrefix,
                "pkgicon",
                true,
                null,
                false,
                200);
    }

    protected void doProcess(
            final ITemplateContext context,
            final IProcessableElementTag tag,
            final IElementTagStructureHandler structureHandler) {

        PkgVersion pkgVersion = derivePkgVersion(context, tag);
        int size = deriveSize(tag);

        final IModelFactory modelFactory = context.getModelFactory();
        final IModel model = modelFactory.createModel();

        IOpenElementTag anchorTag = modelFactory.createOpenElementTag("img");
        anchorTag = modelFactory.setAttribute(anchorTag, "src", getUrl(pkgVersion, size));
        anchorTag = modelFactory.setAttribute(anchorTag, "alt", "icon");
        anchorTag = modelFactory.setAttribute(anchorTag, "width", Integer.toString(size));
        anchorTag = modelFactory.setAttribute(anchorTag, "height", Integer.toString(size));
        model.add(anchorTag);
        model.add(modelFactory.createCloseElementTag("img"));

        structureHandler.replaceWith(model, false);
    }

    private String getUrl(
            final PkgVersion pkgVersion,
            int size) {
        return
                UriComponentsBuilder.newInstance()
                        .pathSegment(PkgIconController.SEGMENT_PKGICON, pkgVersion.getPkg().getName() + ".png")
                        .queryParam(PkgIconController.KEY_FALLBACK,"true")
                        .queryParam(PkgIconController.KEY_SIZE, size == 16 || size == 32 ? size * 2 : size)
                        .queryParam("m", Long.toString(pkgVersion.getPkg().getModifyTimestamp().getTime()))
                        .build()
                        .toString();
    }

    private int deriveSize(
            final IProcessableElementTag tag) {
        return Optional.ofNullable(tag.getAttribute("size"))
                .map(IAttribute::getValue)
                .map(Integer::parseInt)
                .filter(s -> s > 0)
                .orElse(DEFAULT_SIZE);
    }

    private PkgVersion derivePkgVersion(
            final ITemplateContext context,
            final IProcessableElementTag tag) {
        IAttribute paginationAttribute = tag.getAttribute("pkgversion");

        if (null == paginationAttribute) {
            throw new IllegalStateException("the `pkg-version` attribute must be provided");
        }

        final IStandardExpressionParser parser = StandardExpressions.getExpressionParser(context.getConfiguration());
        final IStandardExpression expression = parser.parseExpression(context, paginationAttribute.getValue());
        return (PkgVersion) expression.execute(context);
    }

}
