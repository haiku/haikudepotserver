/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import org.haiku.haikudepotserver.dataobjects.auto._Captcha;

public class Captcha extends _Captcha {

    private static Captcha instance;

    private Captcha() {}

    public static Captcha getInstance() {
        if(instance == null) {
            instance = new Captcha();
        }

        return instance;
    }
}
