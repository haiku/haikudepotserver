/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.api1.model.job.*;
import org.haiku.haikudepotserver.config.BasicConfig;
import org.haiku.haikudepotserver.config.TestBasicConfig;
import org.haiku.haikudepotserver.job.TestJobServiceImpl;
import org.haiku.haikudepotserver.job.model.JobService;
import org.junit.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.EnumSet;

@ContextConfiguration(classes = JobApiIT.SpecificTestConfig.class)
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
        Assertions.assertThat(job.queuedTimestamp.longValue()).isEqualTo(TestJobServiceImpl.DT_1976_FEB.toEpochMilli());
        Assertions.assertThat(job.startTimestamp.longValue()).isEqualTo(TestJobServiceImpl.DT_1976_MAR.toEpochMilli());
        Assertions.assertThat(job.finishTimestamp).isNull();
        Assertions.assertThat(job.ownerUserNickname).isNull();
        Assertions.assertThat(job.jobStatus).isEqualTo(JobStatus.STARTED);

    }

    @Test
    public void testSearchJobs_userOnly() throws Exception {

        {
            ObjectContext context = serverRuntime.newContext();
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
        Assertions.assertThat(job.queuedTimestamp.longValue()).isEqualTo(TestJobServiceImpl.DT_1976_APR.toEpochMilli());
        Assertions.assertThat(job.startTimestamp.longValue()).isEqualTo(TestJobServiceImpl.DT_1976_JUN.toEpochMilli());
        Assertions.assertThat(job.finishTimestamp.longValue()).isEqualTo(TestJobServiceImpl.DT_1976_JUL.toEpochMilli());
        Assertions.assertThat(job.ownerUserNickname).isEqualTo("testuser");
        Assertions.assertThat(job.jobStatus).isEqualTo(JobStatus.FINISHED);

    }

    @Test
    public void testGetJob() throws Exception {
        setAuthenticatedUserToRoot();

        GetJobRequest request = new GetJobRequest();
        request.guid = "started";

        // ------------------------------------
        GetJobResult result = jobApi.getJob(request);
        // ------------------------------------

        Assertions.assertThat(result.guid).isEqualTo("started");
        Assertions.assertThat(result.queuedTimestamp.longValue()).isEqualTo(TestJobServiceImpl.DT_1976_FEB.toEpochMilli());
        Assertions.assertThat(result.startTimestamp.longValue()).isEqualTo(TestJobServiceImpl.DT_1976_MAR.toEpochMilli());
        Assertions.assertThat(result.finishTimestamp).isNull();
        Assertions.assertThat(result.ownerUserNickname).isNull();
        Assertions.assertThat(result.jobStatus).isEqualTo(JobStatus.STARTED);

    }

    @Import({
            TestBasicConfig.class,
            BasicConfig.class
    })
    public final static class SpecificTestConfig {

        @Bean
        JobService jobService() {
            return new TestJobServiceImpl();
        }

    }


}
