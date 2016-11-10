/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job.model;

import org.haiku.haikudepotserver.job.TestJobServiceImpl;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.locks.Lock;

/**
 * <P>This class is used with the {@link TestJobServiceImpl}.</P>
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
    public Optional<Long> tryGetTimeToLiveMillis() {
        return Optional.empty();
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
