/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.miscellaneous;

import java.util.List;

public class GetAllPkgCategoriesResult {

    public List<PkgCategory> pkgCategories;

    public GetAllPkgCategoriesResult() {
    }

    public GetAllPkgCategoriesResult(List<PkgCategory> pkgCategories) {
        this.pkgCategories = pkgCategories;
    }

    public static class PkgCategory {

        public String code;
        public String name;

        public PkgCategory() {
        }

        public PkgCategory(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }

}
