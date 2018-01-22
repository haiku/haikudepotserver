/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.markup;

import com.google.common.base.Preconditions;
import com.google.common.html.HtmlEscapers;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationLookupService;
import org.haiku.haikudepotserver.pkg.model.ResolvedPkgVersionLocalization;
import org.springframework.web.servlet.tags.RequestContextAwareTag;

public class PkgVersionTitleTag extends RequestContextAwareTag {

    private PkgVersion pkgVersion;

    private NaturalLanguage naturalLanguage;

    public PkgVersion getPkgVersion() {
        return pkgVersion;
    }

    public void setPkgVersion(PkgVersion pkgVersion) {
        this.pkgVersion = pkgVersion;
    }

    public NaturalLanguage getNaturalLanguage() {
        return naturalLanguage;
    }

    public void setNaturalLanguage(NaturalLanguage naturalLanguage) {
        this.naturalLanguage = naturalLanguage;
    }

    private ResolvedPkgVersionLocalization getResolvedPkgVersionLocalization() {
        ServerRuntime serverRuntime = ServerRuntime.class.cast(
                pageContext.getRequest().getAttribute(
                        MultipageConstants.KEY_SERVERRUNTIME));
        PkgLocalizationLookupService lookupService = PkgLocalizationLookupService.class.cast(
                pageContext.getRequest().getAttribute(
                        MultipageConstants.KEY_PKGLOCALIZATIONLOOKUPSERVICE));

        return lookupService.resolvePkgVersionLocalization(
                serverRuntime.newContext(),
                getPkgVersion(),
                null,
                getNaturalLanguage());
    }

    @Override
    protected int doStartTagInternal() throws Exception {
        Preconditions.checkNotNull(pkgVersion);
        Preconditions.checkNotNull(naturalLanguage);
        String title = getResolvedPkgVersionLocalization().getTitle();
        pageContext.getOut().print(HtmlEscapers.htmlEscaper().escape(title));
        return SKIP_BODY;
    }
}
