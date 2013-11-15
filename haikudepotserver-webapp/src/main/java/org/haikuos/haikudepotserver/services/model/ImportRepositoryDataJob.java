/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.services.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.net.URL;

/**
 * <p>This object models the request to pull a repository's data into the database locally.  See
 * {@link org.haikuos.haikudepotserver.services.ImportPackageService} for further detail on this.
 * </p>
 */

public class ImportRepositoryDataJob {

    private String code;

    public ImportRepositoryDataJob(String code) {
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

        ImportRepositoryDataJob that = (ImportRepositoryDataJob) o;

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
