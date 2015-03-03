/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import org.haikuos.haikudepotserver.dataobjects.auto._HaikuDepot;

public class HaikuDepot extends _HaikuDepot {

    public enum CacheGroup {
        PKG_VERSION_LOCALIZATION,
        PKG_LOCALIZATION,
        PKG_ICON,
        PKG
    }

    private static HaikuDepot instance;

    private HaikuDepot() {}

    public static HaikuDepot getInstance() {
        if(instance == null) {
            instance = new HaikuDepot();
        }

        return instance;
    }

}
