/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.cayenne;

import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;

public class LikeHelper {

    public static char CHAR_ESCAPE = '|';

    public static Escaper ESCAPER = Escapers
            .builder()
            .addEscape('%',""+CHAR_ESCAPE+'%')
            .addEscape('_',""+CHAR_ESCAPE+'_')
            .build();

}
