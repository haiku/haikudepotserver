/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.miscellaneous;

import java.util.List;

public class GenerateFeedUrlRequest {

    public enum SupplierType {

        /**
         * <p>Provide feed entries from creations of package versions.</p>
         */

        CREATEDPKGVERSION,

        /**
         * <p>Provide feed entries from creations of user ratings.</p>
         */

        CREATEDUSERRATING
    };

    /**
     * <p>If possible, the content may be localized.  In this case, the preference for the language
     * is made by supplying the natural language code.</p>
     */

    public String naturalLanguageCode;

    /**
     * <p>The package names for which the feed may be generated are specified with this.  If the list
     * is empty then the feed will be empty.  If the list is null then the feed will draw from all
     * of the packages.</p>
     */

    public List<String> pkgNames;

    /**
     * <p>This is the limit to the number of entries that will be provided by a given feed.  The
     * feed may have an absolute limit as well; so you may ask for X, but only be provided with
     * less than X items because of the absolute limit.</p>
     */
    public Integer limit;

    /**
     * <p>These are essentially the sources from which the feed will be sourced.</p>
     */

    public List<SupplierType> supplierTypes;

}
