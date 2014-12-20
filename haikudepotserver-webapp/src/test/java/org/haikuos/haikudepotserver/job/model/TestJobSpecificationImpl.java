/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.job.model;

import java.util.Collection;
import java.util.Collections;

/**
 * <P>This class is used with the {@link org.haikuos.haikudepotserver.job.TestJobOrchestrationServiceImpl}.</P>
 */

public class TestJobSpecificationImpl implements JobSpecification {

    private String guid;

    private String ownerUserNickname;

    public TestJobSpecificationImpl(String ownerUserNickname, String guid) {
        this.ownerUserNickname = ownerUserNickname;
        this.guid = guid;
    }

    @Override
    public String getOwnerUserNickname() {
        return ownerUserNickname;
    }

    public void setOwnerUserNickname(String ownerUserNickname) {
        this.ownerUserNickname = ownerUserNickname;
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

    @Override
    public Collection<String> getSuppliedDataGuids() {
        return Collections.emptySet();
    }

}
