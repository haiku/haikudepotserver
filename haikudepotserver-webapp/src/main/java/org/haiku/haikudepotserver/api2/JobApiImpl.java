/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.api2.model.GetJobRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetJobResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchJobsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchJobsResponseEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class JobApiImpl extends AbstractApiImpl implements JobApi {

    private final JobApiService jobApiService;

    public JobApiImpl(JobApiService jobApiService) {
        this.jobApiService = Preconditions.checkNotNull(jobApiService);
    }

    @Override
    public ResponseEntity<GetJobResponseEnvelope> getJob(GetJobRequestEnvelope getJobRequestEnvelope) {
        return ResponseEntity.ok(
                new GetJobResponseEnvelope()
                        .result(jobApiService.getJob(getJobRequestEnvelope)));
    }

    @Override
    public ResponseEntity<SearchJobsResponseEnvelope> searchJobs(SearchJobsRequestEnvelope searchJobsRequestEnvelope) {
        return ResponseEntity.ok(
                new SearchJobsResponseEnvelope()
                        .result(jobApiService.searchJobs(searchJobsRequestEnvelope)));
    }

}
