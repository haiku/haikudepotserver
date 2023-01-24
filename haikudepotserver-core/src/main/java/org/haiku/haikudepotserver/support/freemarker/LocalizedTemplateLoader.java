/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.freemarker;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import freemarker.cache.TemplateLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>This bean provides a repository for freemarker templates.  These templates are used for things like
 * emails.  There is a crude mechanism of localization wherein a different template exists for each natural
 * language.  This object fits into the Freemarker template system.</p>
 */

public class LocalizedTemplateLoader implements TemplateLoader {

    protected static Logger LOGGER = LoggerFactory.getLogger(LocalizedTemplateLoader.class);

    private final static Pattern PATTERN_NAME = Pattern.compile("^([a-z0-9]+-[a-z0-9]+)_([a-z]{2,2})$");

    private final ResourceLoader resourceLoader;

    /**
     * <p>This is a URL such as "classpath:/mail/".</p>
     */

    private final String base;

    public LocalizedTemplateLoader(ResourceLoader resourceLoader, String base) {
        this.resourceLoader = Preconditions.checkNotNull(resourceLoader);
        this.base = Preconditions.checkNotNull(base);
    }

    private Resource getTemplateResource(String naturalLanguageCode, String leafName) {

        Resource rsrc = resourceLoader.getResource(base + leafName + "_" + naturalLanguageCode + ".ftl");

        if (!rsrc.exists()) {
            rsrc = resourceLoader.getResource(base + leafName + ".ftl");
        }

        if(!rsrc.exists()) {
            throw new IllegalStateException("unable to find the template; " + base + leafName);
        }

        return rsrc;
    }

    // ---------------------
    // INTERFACE

    /**
     * <p>The name is a string that is formed by &lt;templatename&gt;_&lt;naturallanguagecode&gt;</p>
     */

    @Override
    public Object findTemplateSource(String name) throws IOException {
        Preconditions.checkState(!Strings.isNullOrEmpty(name));
        Matcher matcher = PATTERN_NAME.matcher(name);

        if (!matcher.matches()) {
            throw new IllegalStateException("illegal template name; "+name);
        }

        return getTemplateResource(matcher.group(2),matcher.group(1));
    }

    @Override
    public Reader getReader(Object templateSource, String encoding) throws IOException {
        Preconditions.checkNotNull(templateSource);
        Preconditions.checkState(Resource.class.isAssignableFrom(templateSource.getClass()));

        if (Strings.isNullOrEmpty(encoding)) {
            encoding = Charsets.UTF_8.name();
        }

        return new InputStreamReader(((Resource)templateSource).getInputStream(), encoding);
    }

    @Override
    public long getLastModified(Object templateSource) {
        return 0; // don't handle this.
    }

    @Override
    public void closeTemplateSource(Object templateSource) throws IOException {
        // don't need to do anything.
    }

}
