/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>A package URL when supplied as a string can be either the URL &quot;naked&quot; or it can be of a form such
 * as;</p>
 *
 * <pre>Foo Bar &lt;http://www.example.com/&gt;</pre>
 *
 * <p>This class is able to parse those forms and model the resultant name and URL.</p>
 */

public class PkgUrl {

    private static Pattern PATTERN_NAMEANDURL = Pattern.compile("^([^<>]*)(\\s+)?<([^<> ]+)>$");

    private String url = null;

    private String name = null;

    private PkgUrlType urlType;

    public PkgUrl(String input, PkgUrlType urlType) {
        super();
        Preconditions.checkNotNull(urlType);
        Preconditions.checkState(!Strings.isNullOrEmpty(input));

        input = input.trim();

        if(Strings.isNullOrEmpty(input)) {
            throw new IllegalStateException("the input must be supplied and should not be an empty string when trimmed.");
        }

        Matcher matcher = PATTERN_NAMEANDURL.matcher(input);

        if(matcher.matches()) {
            this.url = matcher.group(3);
            this.name = Strings.emptyToNull(matcher.group(1).trim());
        }
        else {
            this.url = input;
        }

        this.urlType = urlType;
    }

    public PkgUrlType getUrlType() {
        return urlType;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("%s; %s",urlType.toString(),url);
    }

}
