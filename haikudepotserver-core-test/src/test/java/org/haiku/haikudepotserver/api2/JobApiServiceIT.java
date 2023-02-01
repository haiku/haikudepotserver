/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api2;

import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.api2.model.GetJobRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetJobResult;
import org.haiku.haikudepotserver.api2.model.JobStatus;
import org.haiku.haikudepotserver.api2.model.SearchJobsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchJobsResult;
import org.haiku.haikudepotserver.api2.model.SearchJobsResultItem;
import org.haiku.haikudepotserver.config.BasicConfig;
import org.haiku.haikudepotserver.config.TestBasicConfig;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.TestJobServiceImpl;
import org.haiku.haikudepotserver.job.model.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.List;

@ContextConfiguration(classes = JobApiServiceIT.SpecificTestConfig.class)
public class JobApiServiceIT extends AbstractIntegrationTest {

    @Resource
    private JobApiService jobApiService;

    /**
     * <p>This test will find some data because it is sourced from {@link TestJobServiceImpl}.</p>
     */

    @Test
    public void testSearchJobs_all() {
        setAuthenticatedUserToRoot();

        // ------------------------------------
        SearchJobsResult result = jobApiService.searchJobs(new SearchJobsRequestEnvelope());
        // ------------------------------------

        Assertions.assertThat(result.getItems().size()).isEqualTo(3);
    }

    @Test
    public void testSearchJobs_startedOnly() {
        setAuthenticatedUserToRoot();

        SearchJobsRequestEnvelope request = new SearchJobsRequestEnvelope()
                .statuses(List.of(JobStatus.STARTED));

        // ------------------------------------
        SearchJobsResult result = jobApiService.searchJobs(request);
        // ------------------------------------

        Assertions.assertThat(result.getItems().size()).isEqualTo(1);
        SearchJobsResultItem job = result.getItems().get(0);
        Assertions.assertThat(job.getGuid()).isEqualTo("started");
        Assertions.assertThat(job.getQueuedTimestamp().longValue()).isEqualTo(TestJobServiceImpl.DT_1976_FEB.toEpochMilli());
        Assertions.assertThat(job.getStartTimestamp().longValue()).isEqualTo(TestJobServiceImpl.DT_1976_MAR.toEpochMilli());
        Assertions.assertThat(job.getFinishTimestamp()).isNull();
        Assertions.assertThat(job.getOwnerUserNickname()).isNull();
        Assertions.assertThat(job.getJobStatus()).isEqualTo(JobStatus.STARTED);
    }

    @Test
    public void testSearchJobs_userOnly() {

        {
            ObjectContext context = serverRuntime.newContext();
            User user = integrationTestSupportService.createBasicUser(context, "testuser", "yUe4o2Nwe009"); // language is english
            integrationTestSupportService.agreeToUserUsageConditions(context, user);
            setAuthenticatedUser("testuser");
        }

        SearchJobsRequestEnvelope request = new SearchJobsRequestEnvelope()
                .ownerUserNickname("testuser");

        // ------------------------------------
        SearchJobsResult result = jobApiService.searchJobs(request);
        // ------------------------------------

        Assertions.assertThat(result.getItems().size()).isEqualTo(1);
        SearchJobsResultItem job = result.getItems().get(0);
        Assertions.assertThat(job.getGuid()).isEqualTo("finished");
        Assertions.assertThat(job.getQueuedTimestamp().longValue()).isEqualTo(TestJobServiceImpl.DT_1976_APR.toEpochMilli());
        Assertions.assertThat(job.getStartTimestamp().longValue()).isEqualTo(TestJobServiceImpl.DT_1976_JUN.toEpochMilli());
        Assertions.assertThat(job.getFinishTimestamp().longValue()).isEqualTo(TestJobServiceImpl.DT_1976_JUL.toEpochMilli());
        Assertions.assertThat(job.getOwnerUserNickname()).isEqualTo("testuser");
        Assertions.assertThat(job.getJobStatus()).isEqualTo(JobStatus.FINISHED);
    }

    @Test
    public void testGetJob() {
        setAuthenticatedUserToRoot();

        GetJobRequestEnvelope request = new GetJobRequestEnvelope()
                .guid("started");

        // ------------------------------------
        GetJobResult result = jobApiService.getJob(request);
        // ------------------------------------

        Assertions.assertThat(result.getGuid()).isEqualTo("started");
        Assertions.assertThat(result.getQueuedTimestamp().longValue()).isEqualTo(TestJobServiceImpl.DT_1976_FEB.toEpochMilli());
        Assertions.assertThat(result.getStartTimestamp().longValue()).isEqualTo(TestJobServiceImpl.DT_1976_MAR.toEpochMilli());
        Assertions.assertThat(result.getFinishTimestamp()).isNull();
        Assertions.assertThat(result.getOwnerUserNickname()).isNull();
        Assertions.assertThat(result.getJobStatus()).isEqualTo(JobStatus.STARTED);

    }

    @Import({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            FlywayAutoConfiguration.class,
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
