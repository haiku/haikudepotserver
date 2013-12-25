/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.web;

import com.google.common.base.Optional;
import com.google.common.net.MediaType;
import org.haikuos.haikudepotserver.web.model.WebResourceGroup;
import org.haikuos.haikudepotserver.web.model.WebResourceGroupService;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.servlet.tags.form.AbstractHtmlElementTag;
import org.springframework.web.servlet.tags.form.TagWriter;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import java.io.IOException;

/**
 * <p>This is a JSP tag that is able to be inserted into the "single page" that can render out references
 * to {@link org.haikuos.haikudepotserver.web.model.WebResourceGroup}s that have been configured in the application context.  These are rendered
 * as, for example, "script" tags in the case of JavaScript resources.  The tag is also able to reference
 * the resources by a single URL in which case the application server will respond with a single response
 * that contains all of the resources concatenated to avoid having to make many HTTP requests to the
 * application server to get those.</p>
 */

public class WebResourceGroupTag extends AbstractHtmlElementTag {

    private final static String JS = MediaType.JAVASCRIPT_UTF_8.type() + "/" + MediaType.JAVASCRIPT_UTF_8.subtype();
    private final static String CSS = MediaType.CSS_UTF_8.type() + "/" + MediaType.CSS_UTF_8.subtype();

    private String scriptGroupCode;

    public String getCode() {
        return scriptGroupCode;
    }

    public void setCode(String value) {
        this.scriptGroupCode = value;
    }

    @Override
    protected int writeTagContent(TagWriter tagWriter) throws JspException {

        ServletContext servletContext = pageContext.getServletContext();
        HttpServletResponse response = (HttpServletResponse) pageContext.getResponse();

        ApplicationContext ctx = WebApplicationContextUtils.getWebApplicationContext(pageContext.getServletContext());
        WebResourceGroupService service = ctx.getBean(WebResourceGroupService.class);
        Optional<WebResourceGroup> webResourceGroupOptional = service.getWebResourceGroup(getCode());

        if(!webResourceGroupOptional.isPresent()) {
            throw new JspException("unable to find the web resource group with the code '"+getCode()+"'");
        }

        WebResourceGroup webResourceGroup = webResourceGroupOptional.get();

        // if they are to be separated then render a script tag for each one; otherwise just render one
        // script tag that will join them all together.

        if(webResourceGroup.getSeparated()) {

            try {
                pageContext.getOut().print("\n<!-- web resource group : ");
                pageContext.getOut().print(getCode());
                pageContext.getOut().print(" -->\n");

                for(String resource : webResourceGroup.getResources()) {

                    if(JS.equals(webResourceGroup.getMimeType())) {
                        tagWriter.startTag("script");
                        tagWriter.writeAttribute("src",response.encodeURL(resource));
                        tagWriter.endTag(true);
                    }

                    if(CSS.equals(webResourceGroup.getMimeType())) {
                        tagWriter.startTag("link");
                        tagWriter.writeAttribute("href",response.encodeURL(resource));
                        tagWriter.writeAttribute("rel","stylesheet");
                        tagWriter.endTag();
                    }

                    pageContext.getOut().print("\n");
                }
            }
            catch(IOException ioe) {
                throw new JspException("unable to write out the separated script tags",ioe);
            }
        }
        else {
            if(JS.equals(webResourceGroup.getMimeType())) {
                tagWriter.startTag("script");
                tagWriter.writeAttribute("src",response.encodeURL("/webresourcegroup/"+webResourceGroup.getCode()));
                tagWriter.endTag(true);
            }

            if(CSS.equals(webResourceGroup.getMimeType())) {
                tagWriter.startTag("link");
                tagWriter.writeAttribute("href",response.encodeURL("/webresourcegroup/"+webResourceGroup.getCode()));
                tagWriter.writeAttribute("rel","stylesheet");
                tagWriter.endTag();
            }
        }

        return SKIP_BODY;
    }
}
