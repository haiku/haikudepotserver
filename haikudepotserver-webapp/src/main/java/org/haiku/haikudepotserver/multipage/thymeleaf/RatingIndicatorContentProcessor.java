/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.thymeleaf;

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

import java.util.Locale;

/**
 * <p>This will render a graphic representation of a package's rating in a
 * Thymeleaf template.</p>
 */

public class RatingIndicatorContentProcessor extends AbstractElementTagProcessor {

    private enum StarState {
        OFF("."),
        HALF("o"),
        ON("*");

        private final String alt;

        StarState(String alt) {
            this.alt = alt;
        }

        public String getAlt() {
            return alt;
        }
    }

    public RatingIndicatorContentProcessor(final String dialectPrefix) {
        super(
                TemplateMode.HTML,
                dialectPrefix,
                "ratingindicator",
                true,
                null,
                false,
                200);
    }

    protected void doProcess(
            final ITemplateContext context,
            final IProcessableElementTag tag,
            final IElementTagStructureHandler structureHandler) {
        Float value = deriveValue(context, tag);

        if (null != value) {
            final IModelFactory modelFactory = context.getModelFactory();
            final IModel model = modelFactory.createModel();

            model.add(modelFactory.createOpenElementTag(
                    "span", "class", "rating-indicator"));

            int valueI = (int) (value * 2.0f);

            for (int i = 0; i < 5; i++) {
                addStarToModel(StarState.values()[Math.min(2, valueI)], modelFactory, model);
                valueI = Math.max(0, valueI - 2);
            }

            model.add(modelFactory.createCloseElementTag("span"));

            structureHandler.replaceWith(model, false);
        }
    }

    private void addStarToModel(StarState starState, IModelFactory modelFactory, IModel model) {
        IOpenElementTag imgTag = modelFactory.createOpenElementTag("img");
        imgTag = modelFactory.setAttribute(imgTag, "width", "16");
        imgTag = modelFactory.setAttribute(imgTag, "height", "16");
        imgTag = modelFactory.setAttribute(imgTag, "alt", starState.getAlt());
        imgTag = modelFactory.setAttribute(imgTag,
                "src", "/__img/star" + starState.name().toLowerCase(Locale.ROOT) + ".png");
        model.add(imgTag);
        model.add(modelFactory.createCloseElementTag("img"));
    }

    private Float deriveValue(
            ITemplateContext context,
            IProcessableElementTag tag) {
        IAttribute valueAttribute = tag.getAttribute("value");

        if (null == valueAttribute) {
            throw new IllegalStateException("the `value` attribute must be provided");
        }

        final IStandardExpressionParser parser = StandardExpressions.getExpressionParser(context.getConfiguration());
        final IStandardExpression expression = parser.parseExpression(context, valueAttribute.getValue());
        Float value = (Float) expression.execute(context);

        if (null != value && (value < 0f || value > 5f)) {
            throw new IllegalStateException("the value for the rating indicator must be [0..5]");
        }

        return value;
    }

}
