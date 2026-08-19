/*
 * Copyright 2015-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.web;

public class WebConstants {

    public final static String SEGMENT_JS = "__js";
    public final static String SEGMENT_CSS = "__css";
    public final static String SEGMENT_IMG = "__img";

    public final static String KEY_NATURALLANGUAGECODE = "locale";

    public final static String PATH_COMPONENT_SECURED = "__secured";

    public final static String ANT_PATTERN_JS = String.format("/%s/**", SEGMENT_JS);
    public final static String ANT_PATTERN_CSS = String.format("/%s/**", SEGMENT_CSS);
    public final static String ANT_PATTERN_IMG = String.format("/%s/**", SEGMENT_IMG);

}
