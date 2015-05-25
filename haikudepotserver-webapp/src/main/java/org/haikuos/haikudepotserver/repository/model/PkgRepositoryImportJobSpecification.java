/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.repository.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.haikuos.haikudepotserver.job.model.AbstractJobSpecification;
import org.haikuos.haikudepotserver.job.model.JobSpecification;

/**
 * <p>This object models the request to pull a repository's data into the database locally.
 * </p>
 */

public class PkgRepositoryImportJobSpecification extends AbstractJobSpecification {

    private String repositoryCode;

    public PkgRepositoryImportJobSpecification() {
        super();
    }

    public PkgRepositoryImportJobSpecification(String repositoryCode) {
        this();
        Preconditions.checkNotNull(repositoryCode);
        Preconditions.checkState(!Strings.isNullOrEmpty(repositoryCode));
        this.repositoryCode = repositoryCode;
    }

    public String getRepositoryCode() {
        return repositoryCode;
    }

    public void setRepositoryCode(String value) {
        this.repositoryCode = value;
    }

    @Override
    public boolean isEquivalent(JobSpecification other) {
        Preconditions.checkState(!Strings.isNullOrEmpty(repositoryCode), "this specification should have a repository code defined.");

        if(PkgRepositoryImportJobSpecification.class.isAssignableFrom(other.getClass())) {
            PkgRepositoryImportJobSpecification other2 = (PkgRepositoryImportJobSpecification) other;

            if(Strings.isNullOrEmpty(other2.getRepositoryCode())) {
                throw new IllegalStateException("the other specification should have a code associated with it.");
            }

            return getRepositoryCode().equals(other2.getRepositoryCode());
        }

        return false;
    }
}
