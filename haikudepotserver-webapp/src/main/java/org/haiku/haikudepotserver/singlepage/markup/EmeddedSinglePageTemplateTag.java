/*
 * Copyright 2015-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.singlepage.markup;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import org.springframework.web.servlet.tags.RequestContextAwareTag;
import org.springframework.web.servlet.tags.form.TagWriter;

import javax.servlet.ServletContext;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * <p>Given a path, this tag is able to embed the template into the page so that it doesn't
 * have to make a special HTTP trip to get the template from the server - it has it already
 * on the host page.  This speeds up the initial page load.</p>
 */

public class EmeddedSinglePageTemplateTag extends RequestContextAwareTag {

    private String template;

    public void setTemplate(String template) {
        this.template = template;
    }

    @Override
    protected int doStartTagInternal() throws Exception {

        TagWriter tagWriter = new TagWriter(pageContext.getOut());

        pageContext.getOut().newLine();

        tagWriter.startTag("script");
        tagWriter.writeAttribute("type", "text/ng-template");
        tagWriter.writeAttribute("id", template);

        ServletContext servletContext = getRequestContext().getWebApplicationContext().getServletContext();

        try (InputStream inputStream = servletContext.getResourceAsStream(template)) {
            if (null == inputStream) {
                throw new IllegalStateException("unable to find the template [" + template + "]");
            }
            String templateString = CharStreams.toString(new InputStreamReader(inputStream,Charsets.UTF_8));
            tagWriter.appendValue(templateString);
        }

        tagWriter.endTag();

        pageContext.getOut().newLine();

        return SKIP_BODY;
    }
}
