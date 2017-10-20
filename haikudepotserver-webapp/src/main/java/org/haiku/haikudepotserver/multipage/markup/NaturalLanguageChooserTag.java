/*
 * Copyright 2014-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.markup;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.support.web.NaturalLanguageWebHelper;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.tags.RequestContextAwareTag;
import org.springframework.web.servlet.tags.form.TagWriter;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>This tag renders a list of languages allowing the user to choose one of those languages.</p>
 */

public class NaturalLanguageChooserTag extends RequestContextAwareTag {

    private ObjectContext getObjectContext() {
        return getRequestContext().getWebApplicationContext().getBean(ServerRuntime.class).newContext();
    }

    @Override
    protected int doStartTagInternal() throws Exception {

        ObjectContext context = getObjectContext();
        TagWriter tagWriter = new TagWriter(pageContext.getOut());
        List<NaturalLanguage> naturalLanguages = new ArrayList<>(NaturalLanguage.getAllPopular(context));
        NaturalLanguage currentNaturalLanguage = NaturalLanguageWebHelper.deriveNaturalLanguage(
                context,
                (HttpServletRequest) pageContext.getRequest());

        Collections.sort(naturalLanguages, (o1, o2) -> o1.getCode().compareTo(o2.getCode())
        );

        tagWriter.startTag("span");
        tagWriter.writeAttribute("class","multipage-natural-language-chooser");

        for(int i=0;i<naturalLanguages.size();i++) {

            NaturalLanguage naturalLanguage = naturalLanguages.get(i);

            if(0 != i) {
                tagWriter.appendValue(" ");
            }

            if(currentNaturalLanguage != naturalLanguage) {

                String path = (String) pageContext.getRequest().getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
                UriComponentsBuilder builder = ServletUriComponentsBuilder.newInstance().path(path);
                builder.replaceQueryParam(WebConstants.KEY_NATURALLANGUAGECODE, naturalLanguage.getCode());

                tagWriter.startTag("a");
                tagWriter.writeAttribute("href",builder.build().toString());
                tagWriter.appendValue(naturalLanguage.getCode());
                tagWriter.endTag(); // a

            }
            else {
                tagWriter.startTag("strong");
                tagWriter.writeAttribute("class", "banner-actions-text");
                tagWriter.appendValue(naturalLanguage.getCode());
                tagWriter.endTag(); // strong
            }

        }

        tagWriter.endTag(); // span

        return SKIP_BODY;
    }

}
