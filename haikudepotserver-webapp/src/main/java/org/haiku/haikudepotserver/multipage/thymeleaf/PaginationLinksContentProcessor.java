/*
 * Copyright 2022-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.thymeleaf;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.multipage.model.Pagination;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.*;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.standard.expression.IStandardExpression;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressions;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <p>This will render a list of "page numbers" for the user to jump to view
 * a position within a list.</p>
 */

public class PaginationLinksContentProcessor extends AbstractElementTagProcessor {

    private final static int LINK_COUNT_DEFAULT = 10;

    public PaginationLinksContentProcessor(final String dialectPrefix) {
        super(
                TemplateMode.HTML,
                dialectPrefix,
                "paginationlinks",
                true,
                null,
                false,
                200);
    }

    protected void doProcess(
            final ITemplateContext context,
            final IProcessableElementTag tag,
            final IElementTagStructureHandler structureHandler) {

        HttpServletRequest request = (HttpServletRequest) context.getVariable("request");

        final Pagination pagination = derivePagination(context, tag);
        final int linkCount = getLinkCount(tag);

        List<String> ulClasses = new ArrayList<>();
        ulClasses.add("pagination-control-container");

        if (0 == pagination.getPage()) {
            ulClasses.add("pagination-control-on-first");
        }

        if (pagination.getPage() == pagination.getPages() - 1) {
            ulClasses.add("pagination-control-on-last");
        }

        final IModelFactory modelFactory = context.getModelFactory();
        final IModel model = modelFactory.createModel();

        model.add(modelFactory.createOpenElementTag(
                "ul", "class", String.join(" ", ulClasses)));

        writeArrow(
                modelFactory, model,
                "paginationleft.png",
                "pagination-control-left",
                "<--",
                0 == pagination.getPage() ? ""
                        : deriveHref(request, pagination, pagination.getPage() - 1));

        int[] pageNumbers = pagination.generateSuggestedPages(linkCount);

        for (int pageNumber : pageNumbers) {
            model.add(modelFactory.createOpenElementTag("li"));

            if (pageNumber == pagination.getPage()) {
                IOpenElementTag spanTag = modelFactory.createOpenElementTag("span");
                spanTag = modelFactory.setAttribute(spanTag, "class", "pagination-control-currentpage");
                spanTag = modelFactory.setAttribute(spanTag, "href", "");
                model.add(spanTag);
                model.add(modelFactory.createText(Integer.toString(pageNumber + 1)));
                model.add(modelFactory.createCloseElementTag("span"));
            } else {
                IOpenElementTag anchorTag = modelFactory.createOpenElementTag("a");
                anchorTag = modelFactory.setAttribute(anchorTag, "href", deriveHref(request, pagination, pageNumber));
                model.add(anchorTag);
                model.add(modelFactory.createText(Integer.toString(pageNumber + 1)));
                model.add(modelFactory.createCloseElementTag("a"));
            }

            model.add(modelFactory.createCloseElementTag("li"));
        }

        writeArrow(
                modelFactory, model,
                "paginationright.png",
                "pagination-control-right",
                "-->",
                pagination.getPage() == pagination.getPages() - 1 ? ""
                        : deriveHref(request, pagination, pagination.getPage() + 1));

        model.add(modelFactory.createCloseElementTag("ul"));

        structureHandler.replaceWith(model, false);
    }

    private int getLinkCount(final IProcessableElementTag tag) {
        return Optional.ofNullable(tag.getAttribute("linkCount"))
                .map(Object::toString)
                .map(StringUtils::trimToNull)
                .map(Integer::parseInt)
                .filter(i -> i > 0)
                .orElse(LINK_COUNT_DEFAULT);
    }

    private Pagination derivePagination(
            final ITemplateContext context,
            final IProcessableElementTag tag) {
        IAttribute paginationAttribute = tag.getAttribute("pagination");

        if (null == paginationAttribute) {
            throw new IllegalStateException("the `pagination` attribute must be provided");
        }

        final IStandardExpressionParser parser = StandardExpressions.getExpressionParser(context.getConfiguration());
        final IStandardExpression expression = parser.parseExpression(context, paginationAttribute.getValue());
        return (Pagination) expression.execute(context);
    }

    private String deriveHref(HttpServletRequest request, Pagination pagination, int targetPage) {
        UriComponentsBuilder builder = ServletUriComponentsBuilder.newInstance()
                .replacePath(request.getRequestURI())
                .replaceQuery(request.getQueryString())
                .replaceQueryParam("o", Integer.toString(targetPage * pagination.getMax()));
        return builder.build().toString();
    }

    private void writeArrow(
            final IModelFactory modelFactory, final IModel model,
            String imageFilename,
            String cssClassName,
            String alt,
            String href) {
        model.add(modelFactory.createOpenElementTag("li"));

        IOpenElementTag anchorTag = modelFactory.createOpenElementTag("a");
        anchorTag = modelFactory.setAttribute(anchorTag, "class", cssClassName);
        anchorTag = modelFactory.setAttribute(anchorTag, "href", href);
        anchorTag = modelFactory.setAttribute(anchorTag, "alt", alt);
        model.add(anchorTag);

        model.add(modelFactory.createOpenElementTag(
                "img", "src", "/__img/" + imageFilename));

        model.add(modelFactory.createCloseElementTag("img"));
        model.add(modelFactory.createCloseElementTag("a"));
        model.add(modelFactory.createCloseElementTag("li"));
    }

}
