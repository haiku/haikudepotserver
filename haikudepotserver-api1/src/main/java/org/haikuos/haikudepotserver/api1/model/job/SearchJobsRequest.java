/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.job;

import org.haikuos.haikudepotserver.api1.support.AbstractSearchRequest;

import java.util.Set;

public class SearchJobsRequest extends AbstractSearchRequest {

    public String ownerUserNickname;

    public Set<JobStatus> statuses;

}
