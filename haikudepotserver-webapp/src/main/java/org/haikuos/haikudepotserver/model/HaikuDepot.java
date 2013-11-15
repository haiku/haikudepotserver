/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.model;

import org.haikuos.haikudepotserver.model.auto._HaikuDepot;

public class HaikuDepot extends _HaikuDepot {

    private static HaikuDepot instance;

    private HaikuDepot() {}

    public static HaikuDepot getInstance() {
        if(instance == null) {
            instance = new HaikuDepot();
        }

        return instance;
    }

}
