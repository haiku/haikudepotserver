/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.miscellaneous;

import java.util.List;

public class GetAllArchitecturesResult {

    public List<Architecture> architectures;

    public static class Architecture {
        public String code;

        public Architecture() {
        }

        public Architecture(String code) {
            this.code = code;
        }
    }

}
