package org.haiku.haikudepotserver.singlepage.thymeleaf;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * <p>The SPA consists of many JavaScript source files.  In production these are delivered to the
 * browser as a combined minified file.  In development the individual files are delivered to the
 * browser so that they can be debugged.  This processor takes care of this aspect.</p>
 */
public class AppScriptsProcessor extends AbstractElementTagProcessor {

    private final static String URL_PATH_PREFIX = "/__js";
    private final static String URL_PATH_PRODUCTION = URL_PATH_PREFIX + "/app.concat.min.js";

    private final boolean isProduction;

    private final List<String> nonProductionIndexUrlPaths;

    public AppScriptsProcessor(
            final String dialectPrefix,
            Boolean isProduction,
            Resource nonProductionIndexResource) {
        super(
                TemplateMode.HTML,
                dialectPrefix,
                "appscripts",
                true,
                null,
                true,
                200);
        this.isProduction = BooleanUtils.isTrue(isProduction);
        this.nonProductionIndexUrlPaths = deriveNonProductionIndexPaths(nonProductionIndexResource);
        if (CollectionUtils.isEmpty(nonProductionIndexUrlPaths)) {
            throw new IllegalStateException("expected to have some non-production index url paths available");
        }
    }

    protected void doProcess(
            final ITemplateContext context,
            final IProcessableElementTag tag,
            final IElementTagStructureHandler structureHandler) {

        final IModelFactory modelFactory = context.getModelFactory();
        final IModel model = modelFactory.createModel();

        if (isProduction) {
            addScriptTag(URL_PATH_PRODUCTION, modelFactory, model);
        }
        else {
            nonProductionIndexUrlPaths.forEach(urlPath -> addScriptTag(urlPath, modelFactory, model));
        }

        structureHandler.replaceWith(model, false);
    }

    private void addScriptTag(String urlPath, IModelFactory modelFactory, IModel model) {
        IOpenElementTag scriptTag = modelFactory.createOpenElementTag("script");
        scriptTag = modelFactory.setAttribute(scriptTag, "type", "text/javascript");
        scriptTag = modelFactory.setAttribute(scriptTag, "src", urlPath);
        model.add(scriptTag);
        model.add(modelFactory.createCloseElementTag("script"));
        model.add(modelFactory.createText("\n"));
    }

    private List<String> deriveNonProductionIndexPaths(Resource indexResource) {
        try (InputStream inputStream = indexResource.getInputStream()) {
            return toUrlPaths(inputStream);
        }
        catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private static List<String> toUrlPaths(InputStream inputStream) throws IOException {
        try (
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader)
        ) {
            return toUrlPaths(bufferedReader);
        }
    }

    private static List<String> toUrlPaths(BufferedReader reader) throws IOException {
        String line;
        List<String> result = new ArrayList<>();
        while (null != (line = reader.readLine())) {
            Optional.of(line)
                    .map(StringUtils::trimToNull)
                    .map(s -> URL_PATH_PREFIX + s)
                    .ifPresent(result::add);
        }
        return Collections.unmodifiableList(result);
    }

}
