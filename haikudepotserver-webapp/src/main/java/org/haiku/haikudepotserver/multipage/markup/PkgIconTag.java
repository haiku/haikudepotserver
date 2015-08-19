/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.markup;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.pkg.controller.PkgIconController;
import org.springframework.web.servlet.tags.RequestContextAwareTag;
import org.springframework.web.servlet.tags.form.TagWriter;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * <p>Renders HTML for a package version's icon.</p>
 */

public class PkgIconTag extends RequestContextAwareTag {

    private PkgVersion pkgVersion;

    private int size = 16;

    public PkgVersion getPkgVersion() {
        return pkgVersion;
    }

    public void setPkgVersion(PkgVersion pkgVersion) {
        this.pkgVersion = pkgVersion;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    private String getUrl() {
        return
                UriComponentsBuilder.newInstance()
                        .pathSegment(PkgIconController.SEGMENT_PKGICON, getPkgVersion().getPkg().getName() + ".png")
                        .queryParam(PkgIconController.KEY_FALLBACK,"true")
                        .queryParam(PkgIconController.KEY_SIZE, Integer.toString(getSize()))
                        .queryParam("m", Long.toString(getPkgVersion().getPkg().getModifyTimestamp().getTime()))
                        .build()
                        .toString();
    }

    @Override
    protected int doStartTagInternal() throws Exception {

        Preconditions.checkNotNull(pkgVersion);
        Preconditions.checkState(getSize()==16 || getSize()==32);

        TagWriter tagWriter = new TagWriter(pageContext.getOut());

        tagWriter.startTag("img");
        tagWriter.writeAttribute("src", getUrl());
        tagWriter.writeAttribute("alt", "icon");
        tagWriter.endTag();

        return SKIP_BODY;
    }

}


