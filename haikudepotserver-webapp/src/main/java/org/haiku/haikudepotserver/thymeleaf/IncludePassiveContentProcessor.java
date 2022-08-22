/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.thymeleaf;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.springframework.util.StreamUtils;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IAttribute;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * <p>This will will in passive content from a project resource for the
 * current {@link java.util.Locale}.</p>
 */

public class IncludePassiveContentProcessor extends AbstractElementTagProcessor {

    private static final String PREFIX = "/passivecontent/";

    public IncludePassiveContentProcessor(final String dialectPrefix) {
        super(
                TemplateMode.HTML,
                dialectPrefix,
                "includepassivecontent",
                true,
                null,
                false,
                200);
    }

    protected void doProcess(
            final ITemplateContext context,
            final IProcessableElementTag tag,
            final IElementTagStructureHandler structureHandler) {

        IAttribute attribute = tag.getAttribute("leafname");

        if (null == attribute) {
            throw new IllegalStateException("expected to encounter an attribute `leafname`.");
        }

        String leafname = attribute.getValue();

        if (StringUtils.isBlank(leafname)) {
            throw new IllegalStateException("missing value for attribute `leafname`.");
        }

        try (InputStream inputStream = getIncludeInputStream(context, leafname)) {
            if (null == inputStream) {
                throw new IllegalStateException("unable to find the resource [" + leafname + "]");
            }

            String content = StreamUtils.copyToString(inputStream, Charsets.UTF_8);
            structureHandler.replaceWith(content, false);
        }
        catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private InputStream getIncludeInputStream(final ITemplateContext context, String leafname) {
        String language = context.getLocale().getLanguage();
        InputStream result = null;
        if (!language.equals(NaturalLanguage.CODE_ENGLISH)) {
            result = getIncludeInputStreamForNaturalLanguage(language, leafname);
        }
        if (null == result) {
            result = getIncludeInputStreamForNaturalLanguage(null, leafname);
        }
        return result;
    }

    private String createAdjustedLeafname(String naturalLanguageCode, String leafname) {
        Preconditions.checkState(StringUtils.isNotBlank(leafname), "'leafname' has not been set");

        if (StringUtils.isNotBlank(naturalLanguageCode)) {
            String extension = Files.getFileExtension(leafname);
            String basename = Files.getNameWithoutExtension(leafname);
            return basename + "_" + naturalLanguageCode + "." + extension;
        }

        return leafname;
    }

    private InputStream getIncludeInputStreamForNaturalLanguage(String naturalLanguageCode, String leafname) {
        Preconditions.checkState(StringUtils.isNotBlank(leafname), "'leafname' has not been set");
        String adjustedLeafname = createAdjustedLeafname(naturalLanguageCode, leafname);
        return getClass().getResourceAsStream(PREFIX + adjustedLeafname);
    }

}
