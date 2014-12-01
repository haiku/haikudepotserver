/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.job.model;

import com.google.common.base.CaseFormat;

/**
 * <p>This is an abstract superclass which provides some implementation for a
 * {@link org.haikuos.haikudepotserver.support.job.model.JobSpecification}
 * such as being able to derive the "job type code" from the name of the class.
 * </p>
 */

public abstract class AbstractJobSpecification implements JobSpecification {

    private final static String SUFFIX = "JobSpecification";

    private String guid;

    @Override
    public String getGuid() {
        return guid;
    }

    @Override
    public void setGuid(String guid) {
        this.guid = guid;
    }

    @Override
    public String getJobTypeCode() {
        String sn = this.getClass().getSimpleName();

        if(!sn.endsWith(SUFFIX)) {
            throw new IllegalStateException("malformed job specification concrete class; " + sn);
        }

        return sn.substring(0,sn.length() - SUFFIX.length()).toLowerCase();
    }

    @Override
    public Long getTimeToLive() {
        return null;
    }

    @Override
    abstract public boolean isEquivalent(JobSpecification other);

    @Override
    public String toString() {
        String g = getGuid();
        StringBuilder result = new StringBuilder();

        result.append("js <");

        if(g.length() > 4) {
            result.append(g.substring(0, 4));
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
