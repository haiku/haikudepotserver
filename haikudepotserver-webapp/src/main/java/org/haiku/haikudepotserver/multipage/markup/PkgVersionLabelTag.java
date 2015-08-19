/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.markup;

import com.google.common.base.Preconditions;
import com.google.common.html.HtmlEscapers;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.springframework.web.servlet.tags.RequestContextAwareTag;
import org.springframework.web.servlet.tags.form.TagWriter;

/**
 * <P>Renders the version of coordinates of a package version.</P>
 */

public class PkgVersionLabelTag extends RequestContextAwareTag {

    private PkgVersion pkgVersion;

    public PkgVersion getPkgVersion() {
        return pkgVersion;
    }

    public void setPkgVersion(PkgVersion pkgVersion) {
        this.pkgVersion = pkgVersion;
    }

    @Override
    protected int doStartTagInternal() throws Exception {

        Preconditions.checkNotNull(pkgVersion);

        TagWriter tagWriter = new TagWriter(pageContext.getOut());

        tagWriter.startTag("span");
        tagWriter.appendValue(HtmlEscapers.htmlEscaper().escape(pkgVersion.toVersionCoordinates().toString()));
        tagWriter.endTag();

        return SKIP_BODY;
    }

}
