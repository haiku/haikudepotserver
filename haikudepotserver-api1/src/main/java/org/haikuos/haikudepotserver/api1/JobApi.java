/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haikuos.haikudepotserver.api1.model.job.GetJobRequest;
import org.haikuos.haikudepotserver.api1.model.job.GetJobResult;
import org.haikuos.haikudepotserver.api1.model.job.SearchJobsRequest;
import org.haikuos.haikudepotserver.api1.model.job.SearchJobsResult;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;

/**
 * <P>This API provides generic information about jobs in the system.  This does not provide a means
 * by which jobs are started, but more information about observing the progress around jobs, cancelling
 * jobs etc...</P>
 */

@JsonRpcService("/api/v1/job")
public interface JobApi {

    /**
     * <p>This method will search for jobs as specified in the search jobs request.  It will only return
     * those jobs for the user specified unless no user is specified; in which case it will return all
     * of the jobs.</p>
     */

    SearchJobsResult searchJobs(SearchJobsRequest request) throws ObjectNotFoundException;

    /**
     * <p>This method returns details of the job identified by data in the request.  If there is no such
     * job for the guid supplied, it will throw
     * {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException}.</p>
     */

    GetJobResult getJob(GetJobRequest request) throws ObjectNotFoundException;

}
