/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.thymeleaf;

import com.google.common.html.HtmlEscapers;
import org.apache.commons.lang3.StringUtils;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IAttribute;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.standard.expression.IStandardExpression;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressions;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>This will render plain text as HTML in a Thymeleaf template.</p>
 */

public class PlainTextContentProcessor extends AbstractElementTagProcessor {

    public PlainTextContentProcessor(final String dialectPrefix) {
        super(
                TemplateMode.HTML,
                dialectPrefix,
                "plaintext",
                true,
                null,
                false,
                200);
    }

    protected void doProcess(
            final ITemplateContext context,
            final IProcessableElementTag tag,
            final IElementTagStructureHandler structureHandler) {
        tryGetMarkedUpContent(context, tag)
                .ifPresent(s -> structureHandler.setBody(s,false));
    }

    private Optional<String> tryGetMarkedUpContent(
            final ITemplateContext context,
            final IProcessableElementTag tag) {
        return tryGetContent(context, tag)
                .map(s -> Arrays.stream(s.split("[\n\r]"))
                        .map(s1 -> HtmlEscapers.htmlEscaper().escape(s1))
                        .collect(Collectors.joining("<br/>\n")));
    }

    private Optional<String> tryGetContent(
            final ITemplateContext context,
            final IProcessableElementTag tag) {
        return Optional.ofNullable(tag.getAttribute("content"))
                .map(IAttribute::getValue)
                .map(v -> {
                    final IStandardExpressionParser parser = StandardExpressions.getExpressionParser(context.getConfiguration());
                    final IStandardExpression expression = parser.parseExpression(context, v);
                    return (String) expression.execute(context);
                })
                .map(StringUtils::trimToNull);
    }

}
