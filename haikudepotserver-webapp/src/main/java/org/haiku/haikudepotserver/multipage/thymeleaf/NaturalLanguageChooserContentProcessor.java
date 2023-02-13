/*
 * Copyright 2022-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.thymeleaf;

import com.google.common.base.Preconditions;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.auto._NaturalLanguage;
import org.haiku.haikudepotserver.support.web.NaturalLanguageWebHelper;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.spring6.context.SpringContextUtils;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>This will render a picker for the user to choose a natural language.</p>
 */

public class NaturalLanguageChooserContentProcessor extends AbstractElementTagProcessor {

    public NaturalLanguageChooserContentProcessor(final String dialectPrefix) {
        super(
                TemplateMode.HTML,
                dialectPrefix,
                "naturallanguagechooser",
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
        NaturalLanguagesData naturalLanguagesData = getNaturalLanguagesData(context, request);

        final IModelFactory modelFactory = context.getModelFactory();
        final IModel model = modelFactory.createModel();

        model.add(modelFactory.createOpenElementTag(
                "span",
                "class", "multipage-natural-language-chooser"));
        addElementsForNaturalLanguageData(modelFactory, model, naturalLanguagesData, request);
        model.add(modelFactory.createCloseElementTag("span"));

        structureHandler.replaceWith(model, false);
    }

    private NaturalLanguagesData getNaturalLanguagesData(
            final ITemplateContext context,
            final HttpServletRequest request) {
        Preconditions.checkArgument(null != context);
        Preconditions.checkArgument(null != request);

        final ApplicationContext applicationContext = SpringContextUtils.getApplicationContext(context);
        final ServerRuntime serverRuntime = applicationContext.getBean(ServerRuntime.class);
        ObjectContext objectContext = serverRuntime.newContext();
        return new NaturalLanguagesData(
                NaturalLanguage.getAllPopular(objectContext)
                        .stream()
                        .sorted(Comparator.comparing(_NaturalLanguage::getCode))
                        .collect(Collectors.toList()),
                NaturalLanguageWebHelper.deriveNaturalLanguage(
                        objectContext, request));
    }

    private void addElementsForNaturalLanguageData(
            final IModelFactory modelFactory, final IModel model,
            NaturalLanguagesData naturalLanguagesData,
            HttpServletRequest request) {
        Preconditions.checkArgument(null != naturalLanguagesData);
        Preconditions.checkArgument(null != request);

        List<NaturalLanguage> naturalLanguages = naturalLanguagesData.getNaturalLanguages();

        for (int i = 0; i < naturalLanguages.size(); i++) {

            NaturalLanguage naturalLanguage = naturalLanguages.get(i);

            if (0 != i) {
                model.add(modelFactory.createText(" "));
            }

            if (naturalLanguagesData.getSelectedNaturalLanguage() != naturalLanguage) {
                addElementsForNonCurrentNaturalLanguage(
                        modelFactory, model,
                        naturalLanguage, request);
            } else {
                addElementsForCurrentNaturalLanguage(modelFactory, model, naturalLanguage);
            }
        }
    }

    private void addElementsForNonCurrentNaturalLanguage(
            final IModelFactory modelFactory, final IModel model,
            NaturalLanguage naturalLanguage,
            HttpServletRequest request) {
        UriComponentsBuilder builder = ServletUriComponentsBuilder.newInstance()
                .replacePath(request.getRequestURI())
                .replaceQuery(request.getQueryString())
                .replaceQueryParam(WebConstants.KEY_NATURALLANGUAGECODE, naturalLanguage.getCode());

        model.add(modelFactory.createOpenElementTag(
                "a", "href", builder.build().toString()));
        model.add(modelFactory.createText(naturalLanguage.getCode()));
        model.add(modelFactory.createCloseElementTag("a"));
    }

    private void addElementsForCurrentNaturalLanguage(
            final IModelFactory modelFactory, final IModel model,
            NaturalLanguage naturalLanguage) {
        model.add(modelFactory.createOpenElementTag(
                "strong", "class", "banner-actions-text"));
        model.add(modelFactory.createText(naturalLanguage.getCode()));
        model.add(modelFactory.createCloseElementTag("strong"));
    }

    private static class NaturalLanguagesData {

        private final List<NaturalLanguage> naturalLanguages;
        private final NaturalLanguage selectedNaturalLanguage;

        public NaturalLanguagesData(List<NaturalLanguage> naturalLanguages, NaturalLanguage naturalLanguage) {
            this.naturalLanguages = naturalLanguages;
            this.selectedNaturalLanguage = naturalLanguage;
        }

        public List<NaturalLanguage> getNaturalLanguages() {
            return naturalLanguages;
        }

        public NaturalLanguage getSelectedNaturalLanguage() {
            return selectedNaturalLanguage;
        }
    }

}
