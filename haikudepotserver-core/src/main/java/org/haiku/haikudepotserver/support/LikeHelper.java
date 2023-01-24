/*
 * Copyright 2013-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;

public class LikeHelper {

    private static final char CHAR_ESCAPE = '|';

    public static Escaper ESCAPER = Escapers
            .builder()
            .addEscape('%', "" + CHAR_ESCAPE + '%')
            .addEscape('_', "" + CHAR_ESCAPE + '_')
            .build();

}
