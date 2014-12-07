/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.job.model;

/**
 * <P>This class is used with the {@link org.haikuos.haikudepotserver.support.job.TestJobOrchestrationServiceImpl}.</P>
 */

public class TestJobSpecificationImpl implements JobSpecification {

    private String guid;

    public TestJobSpecificationImpl(String guid) {
        this.guid = guid;
    }

    @Override
    public String getGuid() {
        return guid;
    }

    @Override
    public void setGuid(String value) {
        // no-op
    }

    @Override
    public String getJobTypeCode() {
        return "test";
    }

    @Override
    public Long getTimeToLive() {
        return null;
    }

    @Override
    public boolean isEquivalent(JobSpecification other) {
        return TestJobSpecificationImpl.class.isAssignableFrom(other.getClass());
    }
}
