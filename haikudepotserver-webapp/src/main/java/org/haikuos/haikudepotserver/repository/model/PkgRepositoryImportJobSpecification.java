/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.repository.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.haikuos.haikudepotserver.support.job.model.AbstractJobSpecification;
import org.haikuos.haikudepotserver.support.job.model.JobSpecification;

/**
 * <p>This object models the request to pull a repository's data into the database locally.
 * </p>
 */

public class PkgRepositoryImportJobSpecification extends AbstractJobSpecification {

    private String code;

    public PkgRepositoryImportJobSpecification() {
        super();
    }

    public PkgRepositoryImportJobSpecification(String code) {
        this();
        Preconditions.checkNotNull(code);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String value) {
        this.code = value;
    }

    @Override
    public boolean isEquivalent(JobSpecification other) {
        Preconditions.checkState(!Strings.isNullOrEmpty(code), "this specification should have a code defined.");

        if(PkgRepositoryImportJobSpecification.class.isAssignableFrom(other.getClass())) {
            PkgRepositoryImportJobSpecification other2 = (PkgRepositoryImportJobSpecification) other;

            if(Strings.isNullOrEmpty(other2.getCode())) {
                throw new IllegalStateException("the other specification should have a code associated with it.");
            }

            return code.equals(other2.getCode());
        }

        return false;
    }
}
