/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.AbstractIntegrationTest;
import org.haikuos.haikudepotserver.api1.model.job.JobStatus;
import org.haikuos.haikudepotserver.api1.model.job.SearchJobsRequest;
import org.haikuos.haikudepotserver.api1.model.job.SearchJobsResult;
import org.haikuos.haikudepotserver.support.job.TestJobOrchestrationServiceImpl;
import org.junit.Test;

import javax.annotation.Resource;
import java.util.EnumSet;

public class JobApiIT extends AbstractIntegrationTest {

    @Resource
    private JobApi jobApi;

    @Test
    public void testSearchJobs_all() throws Exception {
        setAuthenticatedUserToRoot();

        // ------------------------------------
        SearchJobsResult result = jobApi.searchJobs(new SearchJobsRequest());
        // ------------------------------------

        Assertions.assertThat(result.items.size()).isEqualTo(3);

    }

    @Test
    public void testSearchJobs_startedOnly() throws Exception {
        setAuthenticatedUserToRoot();

        SearchJobsRequest request = new SearchJobsRequest();
        request.statuses = EnumSet.of(JobStatus.STARTED);

        // ------------------------------------
        SearchJobsResult result = jobApi.searchJobs(request);
        // ------------------------------------

        Assertions.assertThat(result.items.size()).isEqualTo(1);
        SearchJobsResult.Job job = result.items.get(0);
        Assertions.assertThat(job.guid).isEqualTo("started");
        Assertions.assertThat(job.queuedTimestamp.longValue()).isEqualTo(TestJobOrchestrationServiceImpl.DT_1976_FEB.getMillis());
        Assertions.assertThat(job.startTimestamp.longValue()).isEqualTo(TestJobOrchestrationServiceImpl.DT_1976_MAR.getMillis());
        Assertions.assertThat(job.finishTimestamp).isNull();
        Assertions.assertThat(job.ownerUserNickname).isNull();
        Assertions.assertThat(job.jobStatus).isEqualTo(JobStatus.STARTED);

    }

    @Test
    public void testSearchJobs_userOnly() throws Exception {

        {
            ObjectContext context = serverRuntime.getContext();
            integrationTestSupportService.createBasicUser(context, "testuser", "yUe4o2Nwe009"); // language is english
            setAuthenticatedUser("testuser");
        }

        SearchJobsRequest request = new SearchJobsRequest();
        request.ownerUserNickname = "testuser";

        // ------------------------------------
        SearchJobsResult result = jobApi.searchJobs(request);
        // ------------------------------------

        Assertions.assertThat(result.items.size()).isEqualTo(1);
        SearchJobsResult.Job job = result.items.get(0);
        Assertions.assertThat(job.guid).isEqualTo("finished");
        Assertions.assertThat(job.queuedTimestamp.longValue()).isEqualTo(TestJobOrchestrationServiceImpl.DT_1976_APR.getMillis());
        Assertions.assertThat(job.startTimestamp.longValue()).isEqualTo(TestJobOrchestrationServiceImpl.DT_1976_JUN.getMillis());
        Assertions.assertThat(job.finishTimestamp.longValue()).isEqualTo(TestJobOrchestrationServiceImpl.DT_1976_JUL.getMillis());
        Assertions.assertThat(job.ownerUserNickname).isEqualTo("testuser");
        Assertions.assertThat(job.jobStatus).isEqualTo(JobStatus.FINISHED);

    }

}
