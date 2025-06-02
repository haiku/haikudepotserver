/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.storage.job;

import jakarta.annotation.Resource;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.storage.model.DataStorageGarbageCollectionJobSpecification;
import org.haiku.haikudepotserver.support.PgDataStorageTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.shaded.com.google.common.collect.ImmutableSet;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ContextConfiguration(classes = TestConfig.class)
public class DataStorageGarbageCollectionJobRunnerIT extends AbstractIntegrationTest {

    private final static byte[] SAMPLE_PAYLOAD = new byte[] { 0x0a };

    @Resource
    private DataSource dataSource;

    @Resource
    private JobService jobService;

    /**
     * <p>Sets up a scenario in which there is some data to delete.</p>
     */

    @Test
    public void testRun() throws Exception {

        // expected to be deleted
        setupDatas(Duration.ofHours(2), "2HAGO", List.of(SAMPLE_PAYLOAD));

        // expected to remain
        setupDatas(Duration.ofMinutes(10), "10MAGO", List.of(SAMPLE_PAYLOAD));

        DataStorageGarbageCollectionJobSpecification specification = new DataStorageGarbageCollectionJobSpecification();
        specification.setOlderThanMillis(Duration.ofHours(1).toMillis());

        // ------------------------------------
        String guid = jobService.submit(specification, JobSnapshot.COALESCE_STATUSES_NONE);
        // ------------------------------------

        // check the job has finished OK.
        jobService.awaitJobFinishedUninterruptibly(guid, 10000);
        Optional<? extends JobSnapshot> snapshotOptional = jobService.tryGetJob(guid);
        Assertions.assertThat(snapshotOptional.get().getStatus()).isEqualTo(JobSnapshot.Status.FINISHED);

        // check which data has been deleted. Note that there may be other data stored from other jobs.
        Assertions.assertThat(getDataCodes()).contains("10MAGO");
        Assertions.assertThat(getDataCodes()).excludes("2HAGO");
    }

    private Set<String> getDataCodes() throws SQLException {
        String getDataCodesSql = "select code from datastore.object_head";
        ImmutableSet.Builder<String> resultBuilder = new ImmutableSet.Builder<>();

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(getDataCodesSql);
                ResultSet resultSet = preparedStatement.executeQuery()
        ) {
            while (resultSet.next()) {
                resultBuilder.add(resultSet.getString(1));
            }
        }

        return resultBuilder.build();
    }

    /**
     * <p>This will write data to database under a code so that the read operations can be performed against it.</p>
     */

    private void setupDatas(Duration timestampAgo, String code, List<byte[]> datas) throws SQLException {
        PgDataStorageTestHelper.setupDatas(
                dataSource,
                new java.sql.Timestamp(Clock.systemUTC().millis() - timestampAgo.toMillis()),
                code,
                datas);
    }

}
