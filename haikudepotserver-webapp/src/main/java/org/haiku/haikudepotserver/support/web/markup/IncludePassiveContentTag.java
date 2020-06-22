package org.haiku.haikudepotserver.support.web.markup;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.springframework.web.servlet.tags.RequestContextAwareTag;

import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * <p>This is like the regular <code>include</code> tag except that it will
 * try to find a localized variant of the resource before falling back to
 * English as a default.</p>
 */

public class IncludePassiveContentTag extends RequestContextAwareTag {

    private static final String PREFIX = "/WEB-INF/passivecontent/";

    private String leafname;

    public String getLeafname() {
        return leafname;
    }

    public void setLeafname(String leafname) {
        this.leafname = leafname;
    }

    private InputStream getIncludeInputStream() {
        String language = getRequestContext().getLocale().getLanguage();
        InputStream result = null;
        if (!language.equals(NaturalLanguage.CODE_ENGLISH)) {
            result = getIncludeInputStream(language);
        }
        if (null == result) {
            result = getIncludeInputStream(null);
        }
        return result;
    }

    private String createAdjustedLeafname(String naturalLanguageCode) {
        Preconditions.checkState(StringUtils.isNotBlank(leafname), "'leafname' has not been set");

        if (StringUtils.isNotBlank(naturalLanguageCode)) {
            String extension = Files.getFileExtension(leafname);
            String basename = Files.getNameWithoutExtension(leafname);
            return basename + "_" + naturalLanguageCode + "." + extension;
        }

        return leafname;
    }

    private InputStream getIncludeInputStream(String naturalLanguageCode) {
        Preconditions.checkState(StringUtils.isNotBlank(leafname), "'leafname' has not been set");
        String adjustedLeafname = createAdjustedLeafname(naturalLanguageCode);
        return getRequestContext().getWebApplicationContext().getServletContext().getResourceAsStream(PREFIX + adjustedLeafname);
    }

    @Override
    protected int doStartTagInternal() throws Exception {
        Preconditions.checkNotNull(getLeafname());
        try (InputStream inputStream = getIncludeInputStream()) {
            if (null == inputStream) {
                throw new IllegalStateException("unable to find the resource [" + getLeafname() + "]");
            }
            InputStreamReader reader = new InputStreamReader(inputStream, Charsets.UTF_8);
            CharStreams.copy(reader, pageContext.getOut());
        }
        return SKIP_BODY;
    }

}
