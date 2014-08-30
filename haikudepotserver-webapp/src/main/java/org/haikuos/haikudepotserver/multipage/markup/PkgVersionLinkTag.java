/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.multipage.markup;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.html.HtmlEscapers;
import org.haikuos.haikudepotserver.dataobjects.PkgVersion;
import org.haikuos.haikudepotserver.multipage.MultipageConstants;
import org.springframework.web.servlet.tags.RequestContextAwareTag;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import java.io.IOException;

/**
 * <p>This tag renders a link to a package version.</p>
 */

public class PkgVersionLinkTag extends RequestContextAwareTag {

    private PkgVersion pkgVersion;

    public PkgVersion getPkgVersion() {
        return pkgVersion;
    }

    public void setPkgVersion(PkgVersion pkgVersion) {
        this.pkgVersion = pkgVersion;
    }

    private String emptyToHyphen(String part) {
        if(Strings.isNullOrEmpty(part)) {
            return "-";
        }

        return part;
    }

    @Override
    protected int doStartTagInternal() throws Exception {

        Preconditions.checkNotNull(getPkgVersion());

        JspWriter jspWriter = pageContext.getOut();

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(String.format(
                "%s/pkg/%s/%s/%s/%s/%s/%s/%s",
                MultipageConstants.PATH_MULTIPAGE,
                pkgVersion.getPkg().getName(),
                emptyToHyphen(pkgVersion.getMajor()),
                emptyToHyphen(pkgVersion.getMinor()),
                emptyToHyphen(pkgVersion.getMicro()),
                emptyToHyphen(pkgVersion.getPreRelease()),
                null == pkgVersion.getRevision() ? "-" : pkgVersion.getRevision().toString(),
                pkgVersion.getArchitecture().getCode()));

        String naturalLanguageCode = pageContext.getRequest().getParameter(MultipageConstants.KEY_NATURALLANGUAGECODE);

        if(!Strings.isNullOrEmpty(naturalLanguageCode)) {
            builder.queryParam(
                    MultipageConstants.KEY_NATURALLANGUAGECODE,
                    naturalLanguageCode);
        }

        jspWriter.print("<a href=\"");
        jspWriter.print(HtmlEscapers.htmlEscaper().escape(builder.build().toString()));
        jspWriter.print("\">");

        return EVAL_BODY_INCLUDE;
    }

    @Override
    public int doEndTag() throws JspException {
        JspWriter jspWriter = pageContext.getOut();

        try {
            jspWriter.print("</a>");
        }
        catch(IOException ioe) {
            throw new JspException("unable to write the end of the pkg version link", ioe);
        }

        return EVAL_PAGE;
    }
}
