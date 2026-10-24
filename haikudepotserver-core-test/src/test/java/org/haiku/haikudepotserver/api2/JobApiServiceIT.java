/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api2;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractCoreIntegrationTest;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.CapturingMailSender;
import org.haiku.haikudepotserver.api2.model.*;
import org.haiku.haikudepotserver.config.CoreBasicConfig;
import org.haiku.haikudepotserver.config.CorePersistenceConfig;
import org.haiku.haikudepotserver.config.CoreTestConfig;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.TestJobServiceImpl;
import org.haiku.haikudepotserver.job.model.BulkDataJobCoordinatorService;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.storage.model.DataStorageService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.mail.MailSender;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;

@SpringBootTest(classes = JobApiServiceIT.SpecificTestConfig.class)
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

        Assertions.assertThat(result.getItems()).hasSize(3);
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

    /**
     * <p>This is a clone of {@link CoreTestConfig} except that it's populating a special
     * {@link JobService} and {@link DataStorageService} which are required to support the
     * tests in the outer class.</p>
     */

    @Import({

            // Things that would typically be provided by SpringBoot.
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,

            // Core configurations to setup things like object-mappers and ORMs
            CoreBasicConfig.class,
            CorePersistenceConfig.class,

    })
    @PropertySource(value = "classpath:test-local.properties")
    @ComponentScan(basePackages = "org.haiku.haikudepotserver")
    public final static class SpecificTestConfig {

        @Bean
        JobService jobService() {
            return new TestJobServiceImpl();
        }

        @Bean
        DataStorageService dataStorageService() {
            return Mockito.mock(DataStorageService.class);
        }

        @Bean
        BulkDataJobCoordinatorService bulkDataJobCoordinatorService() {
            return Mockito.mock(BulkDataJobCoordinatorService.class);
        }

        @Bean
        public MeterRegistry meterRegistry() {
            return Mockito.mock(MeterRegistry.class);
        }

        @Bean
        public MailSender mailSender() {
            return new CapturingMailSender();
        }

        @Bean("messageSourceBaseNames")
        public List<String> messageSourceBaseNames() {
            return CoreTestConfig.MESSAGE_SOURCE_BASE_NAMES;
        }
    }

}
