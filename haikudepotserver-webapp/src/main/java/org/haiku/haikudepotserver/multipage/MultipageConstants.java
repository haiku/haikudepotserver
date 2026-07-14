/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage;

import org.haiku.haikudepotserver.support.web.WebConstants;

import java.util.Collection;
import java.util.Set;

public class MultipageConstants {

    public final static String SEGMENT_MULTIPAGE = "__multipage";

    public final static String PATH_MULTIPAGE = "/" + SEGMENT_MULTIPAGE;

    // Model parameters
    public final static String KEY_DATA = "data";
    public final static String KEY_INTERNATIONALIZATION_SUPPLIER = "internationalizationSupplier";

    // Query parameters
    public final static String KEY_ARCHITECTURECODE = "arch";
    public final static String KEY_OFFSET = "o";
    public final static String KEY_PKGCATEGORYCODE = "pkgcat";
    public final static String KEY_VERSION_MAJOR = "vmajor";
    public final static String KEY_VERSION_MINOR = "vminor";
    public final static String KEY_VERSION_MICRO = "vmicro";
    public final static String KEY_VERSION_PRERELEASE = "vprel";
    public final static String KEY_VERSION_REVISION = "vrev";
    public final static String KEY_SEARCHEXPRESSION = "srchexpr";

    /**
     * <p>These are query or form parameters that should be relayed from one page to
     * another to maintain quasi-state.</p>
     */
    public final static Collection<String> KEYS_RELAY_PARAMETERS = Set.of(
            WebConstants.KEY_NATURALLANGUAGECODE
    );

}
