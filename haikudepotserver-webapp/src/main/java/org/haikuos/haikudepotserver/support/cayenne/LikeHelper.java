/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.cayenne;

public class LikeHelper {

    public static char CHAR_ESCAPE = '|';

    public static String escapeExpression(String value) {
        return value.replace("%","|%").replace("_","|_");
    }

}
