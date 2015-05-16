/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.multipage.markup;

import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.haikuos.haikudepotserver.dataobjects.PkgIcon;
import org.haikuos.haikudepotserver.dataobjects.PkgVersion;
import org.springframework.web.servlet.tags.RequestContextAwareTag;
import org.springframework.web.servlet.tags.form.TagWriter;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

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
        ObjectContext context = getPkgVersion().getObjectContext();
        Optional<org.haikuos.haikudepotserver.dataobjects.MediaType> pngOptional =
                org.haikuos.haikudepotserver.dataobjects.MediaType.getByCode(context, MediaType.PNG.toString());
        Optional<PkgIcon> pkgIconOptional = pkgVersion.getPkg().getPkgIcon(pngOptional.get(), getSize());

        if(pkgIconOptional.isPresent()) {
            return
                    UriComponentsBuilder.newInstance()
                    .pathSegment("pkgicon", getPkgVersion().getPkg().getName() + ".png")
                    .queryParam("f","true")
                            .queryParam("s",Integer.toString(getSize()))
                    .queryParam("m",Long.toString(getPkgVersion().getPkg().getModifyTimestamp().getTime()))
                    .build()
                    .toString();
        }
        else {
            switch(size) {
                case 16:
                    return "/img/generic16.png";

                case 32:
                    return "/img/generic32.png";

                default:
                    throw new IllegalStateException("unknown size for default icon");
            }
        }
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


