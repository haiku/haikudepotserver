package org.haiku.haikudepotserver.job;

import jakarta.annotation.Resource;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import java.util.Set;

@ContextConfiguration(classes = TestConfig.class)
public class DbJobDataStorageInUseCheckerIT extends AbstractIntegrationTest {

    @Resource
    private DbJobDataStorageInUseChecker checker;

    /**
     * <p>Setup a basic {@link Job} with some {@link JobData}s and then check to see
     * if the {@link JobData} datas are detected as being in use.</p>
     */

    @Test
    public void testHappyDays() {

        {
            ObjectContext context = serverRuntime.newContext();

            JobType jobType = context.newObject(JobType.class);
            jobType.setCode("jobgarbagecollection");

            JobDataMediaType jobDataMediaType = context.newObject(JobDataMediaType.class);
            jobDataMediaType.setCode("thing/thing");

            JobAssignment jobAssignment = context.newObject(JobAssignment.class);
            jobAssignment.setCode("e42a9855-fc4c-4c24-be0b-3f7a2a4b49e0");

            Job job = context.newObject(Job.class);
            job.setJobType(jobType);
            job.setCode("e42a9855-fc4c-4c24-be0b-3f7a2a4b49e0");
            job.setOwnerUserNickname("john");
            job.setQueueTimestamp(new java.sql.Timestamp(0L));
            job.setExpiryTimestamp(new java.sql.Timestamp(0L));
            job.setSpecification("{}");
            job.setJobAssignment(jobAssignment);

            JobData jobData = context.newObject(JobData.class);
            jobData.setJob(job);
            jobData.setCode("0cc1203e-f7c0-4db1-80f9-4e52b50b5edf");
            jobData.setStorageCode("e3a6d5e6-51fb-4b3b-ad73-9bd38364d14d");
            jobData.setUseCode("saving");
            jobData.setJobDataType(JobDataType.getSupplied(context));
            jobData.setJobDataEncoding(JobDataEncoding.getByCode(
                    context, org.haiku.haikudepotserver.job.model.JobDataEncoding.NONE.lowerName()));
            jobData.setJobDataMediaType(jobDataMediaType);

            context.commitChanges();
        }

        // -------------------------
        Set<String> codesInUse = checker.inUse(Set.of(
                "e3a6d5e6-51fb-4b3b-ad73-9bd38364d14d", // used with job
                "15b29d65-c503-400f-8d4c-a6e56c269a48" // not used with job
        ));
        // -------------------------

        Assertions.assertThat(codesInUse).containsOnly("e3a6d5e6-51fb-4b3b-ad73-9bd38364d14d");
    }

}
