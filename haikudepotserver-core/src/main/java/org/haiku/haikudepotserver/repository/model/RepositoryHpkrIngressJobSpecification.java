/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobSpecification;

import java.util.Objects;
import java.util.Set;

/**
 * <p>This object models the request to pull a repository's data into the database locally.
 * The repository source codes are optional; it can be null in which case all of the sources
 * will be imported.
 * </p>
 */

public class RepositoryHpkrIngressJobSpecification extends AbstractJobSpecification {

    private String repositoryCode;

    private Set<String> repositorySourceCodes;

    public RepositoryHpkrIngressJobSpecification() {
        super();
    }

    public RepositoryHpkrIngressJobSpecification(String repositoryCode) {
        this(repositoryCode, null);
    }

    public RepositoryHpkrIngressJobSpecification(
            String repositoryCode,
            Set<String> repositorySourceCodes) {
        this();
        Preconditions.checkNotNull(repositoryCode);
        Preconditions.checkState(!Strings.isNullOrEmpty(repositoryCode));
        this.repositoryCode = repositoryCode;
        this.repositorySourceCodes = repositorySourceCodes;
    }

    public String getRepositoryCode() {
        return repositoryCode;
    }

    public void setRepositoryCode(String value) {
        this.repositoryCode = value;
    }

    public Set<String> getRepositorySourceCodes() {
        return repositorySourceCodes;
    }

    public void setRepositorySourceCodes(Set<String> repositorySourceCodes) {
        this.repositorySourceCodes = repositorySourceCodes;
    }

    @Override
    public boolean isEquivalent(JobSpecification other) {
        Preconditions.checkState(!Strings.isNullOrEmpty(repositoryCode), "this specification should have a repository code defined.");

        if(super.isEquivalent(other)) {
            RepositoryHpkrIngressJobSpecification other2 = (RepositoryHpkrIngressJobSpecification) other;

            return
                    Objects.equals(getRepositoryCode(), other2.getRepositoryCode()) &&
                            Objects.equals(getRepositorySourceCodes(), other2.getRepositorySourceCodes());
        }

        return false;
    }

}
