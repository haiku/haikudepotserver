/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.repository.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * <p>This object models the request to pull a repository's data into the database locally.
 * </p>
 */

public class PkgRepositoryImportJob {

    private String code;

    public PkgRepositoryImportJob(String code) {
        super();
        Preconditions.checkNotNull(code);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PkgRepositoryImportJob that = (PkgRepositoryImportJob) o;

        if (!code.equals(that.code)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return getCode();
    }

}
