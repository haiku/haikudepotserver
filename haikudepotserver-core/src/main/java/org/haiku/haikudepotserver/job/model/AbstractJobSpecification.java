/*
 * Copyright 2014-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * <p>This is an abstract superclass which provides some implementation for a
 * {@link JobSpecification}
 * such as being able to derive the "job type code" from the name of the class.
 * </p>
 */

public abstract class AbstractJobSpecification implements JobSpecification {

    private final static String SUFFIX = "JobSpecification";

    private final static Random RANDOM = new Random();

    private String ownerUserNickname;

    private String guid;

    @Override
    public String getGuid() {
        return guid;
    }

    @Override
    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getOwnerUserNickname() {
        return ownerUserNickname;
    }

    public void setOwnerUserNickname(String ownerUserNickname) {
        this.ownerUserNickname = ownerUserNickname;
    }

    @JsonIgnore
    @Override
    public String getJobTypeCode() {
        String sn = this.getClass().getSimpleName();

        if(!sn.endsWith(SUFFIX)) {
            throw new IllegalStateException("malformed job specification concrete class; " + sn);
        }

        return sn.substring(0, sn.length() - SUFFIX.length()).toLowerCase();
    }

    @JsonIgnore
    @Override
    public Optional<Long> tryGetTimeToLiveMillis() {
        return Optional.empty();
    }

    @JsonIgnore
    @Override
    public Collection<String> getSuppliedDataGuids() {
        return Collections.emptySet();
    }

    @JsonIgnore
    @Override
    public boolean isEquivalent(JobSpecification other) {
        return this.getClass().isAssignableFrom(other.getClass()) &&
                Objects.equals(other.getOwnerUserNickname(), getOwnerUserNickname());
    }

    /**
     * @return a fraction of a single unit of the magnitude that can be added or removed from the TTL so that
     *  the TTL is slightly stochastic.
     */
    protected long createTimeToLiveJitterMillis(TimeUnit magnitude) {
        return RANDOM.nextLong() % magnitude.toMillis(1);
    }

    @Override
    public String toString() {
        String g = getGuid();
        StringBuilder result = new StringBuilder();

        result.append("js <");

        if(g.length() > 4) {
            result.append(g, 0, 4);
            result.append("..");
        }
        else {
            result.append(g);
        }

        result.append("> (");
        result.append(getJobTypeCode());
        result.append(")");

        return result.toString();
    }

}
