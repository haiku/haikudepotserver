/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support;

import jakarta.servlet.http.HttpServletResponse;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.job.model.Job;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgDumpExportJobSpecification;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public class ControllerHelperTest {

    /**
     * Test how the system functions when the job data is newer or as new as the modified timestamp.
     */

    @Test
    public void testMaybeRedirectToJobData_notModified() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        JobService jobService = Mockito.mock(JobService.class);
        Job jobSnapshot = new Job();

        Instant jobInstant = LocalDateTime.of(2026, 6, 10, 14, 14, 14)
                .toInstant(ZoneOffset.UTC);
        jobSnapshot.setDataTimestamp(Date.from(jobInstant));
        jobSnapshot.setStartTimestamp(Date.from(jobInstant.plus(1, ChronoUnit.MINUTES)));
        jobSnapshot.setQueuedTimestamp(Date.from(jobInstant));
        jobSnapshot.setStartTimestamp(Date.from(jobInstant.plus(1, ChronoUnit.MINUTES)));
        jobSnapshot.setFinishTimestamp(Date.from(jobInstant.plus(2, ChronoUnit.MINUTES)));
        jobSnapshot.setJobSpecification(anyJobSpecification());
        jobSnapshot.addGeneratedDataGuid("8bb0e7ad-3ec1-4f85-9a17-a2231b61cb60");

        Mockito.when(jobService.tryGetJob(Mockito.eq("e969bc19-5ad2-4e9e-83f7-d06b20e4e89c")))
                .thenAnswer(_ -> Optional.of(jobSnapshot));

        // ------------------------------------
        ControllerHelper.maybeRedirectToJobData(
                jobService,
                response,
                "e969bc19-5ad2-4e9e-83f7-d06b20e4e89c",
                "Wed, 10 Jun 2026 15:14:14 GMT", // <- note; 1h later
                "HaikuDepot/0.0.10");
        // ------------------------------------

        Assertions.assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_NOT_MODIFIED);
    }

    @Test
    public void testMaybeRedirectToJobData_finishedRedirects() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        JobService jobService = Mockito.mock(JobService.class);
        Job jobSnapshot = new Job();
        jobSnapshot.setStartTimestamp();
        jobSnapshot.setFinishTimestamp();
        jobSnapshot.setJobSpecification(anyJobSpecification());
        jobSnapshot.addGeneratedDataGuid("936e4d5a-8470-4fb0-8d94-647394e3cfd7");

        Mockito.when(jobService.tryGetJob(Mockito.eq("e969bc19-5ad2-4e9e-83f7-d06b20e4e89c")))
                .thenAnswer(_ -> Optional.of(jobSnapshot));

        // ------------------------------------
        ControllerHelper.maybeRedirectToJobData(
                jobService,
                response,
                "e969bc19-5ad2-4e9e-83f7-d06b20e4e89c",
                "Wed, 21 Oct 2015 07:28:00 GMT",
                "HaikuDepot/0.0.10");
        // ------------------------------------

        Assertions.assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_MOVED_TEMPORARILY);
        Assertions.assertThat(response.getHeader("Location"))
                .isEqualTo("/__secured/jobdata/936e4d5a-8470-4fb0-8d94-647394e3cfd7/download");
    }

    /**
     * <p>If the job is not yet ready then the response will be a 503.</p>
     */
    @Test
    public void testMaybeRedirectToJobData_notFinished503() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        JobService jobService = Mockito.mock(JobService.class);
        Job jobSnapshot = new Job();
        jobSnapshot.setStartTimestamp();
        // no finished timestamp so is not finished.
        jobSnapshot.setJobSpecification(anyJobSpecification());

        Mockito.when(jobService.tryGetJob(Mockito.eq("e969bc19-5ad2-4e9e-83f7-d06b20e4e89c")))
                .thenAnswer(_ -> Optional.of(jobSnapshot));

        // ------------------------------------
        ControllerHelper.maybeRedirectToJobData(
                jobService,
                response,
                "e969bc19-5ad2-4e9e-83f7-d06b20e4e89c",
                "Wed, 21 Oct 2015 07:28:00 GMT",
                "HaikuDepot/0.0.10");
        // ------------------------------------

        Assertions.assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        Assertions.assertThat(response.getHeader("Retry-After")).isEqualTo("5");
    }

    /**
     * The old HaikUDepot client does not support the server asking the client to try again later so
     * this logic will test that it tries to complete the job before returning.
     */

    // TODO (andponlin) remove this once the older version of HaikuDepot is no longer supported.
    @Test
    public void testMaybeRedirectToJobData_notFinishedThenFinishedOlderVersion() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        JobService jobService = Mockito.mock(JobService.class);

        Job jobSnapshotBefore = new Job();
        jobSnapshotBefore.setStartTimestamp();
        // note; no finish timestamp so will retry
        jobSnapshotBefore.addGeneratedDataGuid("936e4d5a-8470-4fb0-8d94-647394e3cfd7");
        jobSnapshotBefore.setJobSpecification(anyJobSpecification());

        Job jobSnapshotAfter = new Job();
        jobSnapshotAfter.setQueuedTimestamp();
        jobSnapshotAfter.setStartTimestamp();
        jobSnapshotAfter.setFinishTimestamp();
        jobSnapshotAfter.addGeneratedDataGuid("91cf45b0-97ea-4cdf-a339-6f85ced6aa4f");
        jobSnapshotBefore.setJobSpecification(anyJobSpecification());

        Mockito.when(jobService.tryGetJob(Mockito.eq("e969bc19-5ad2-4e9e-83f7-d06b20e4e89c")))
                .thenAnswer(_ -> Optional.of(jobSnapshotBefore));

        Mockito.when(jobService.awaitJobFinishedUninterruptibly(Mockito.eq("1e3e0ddd-ac38-4929-bd0b-56dc001c4c5a"), Mockito.anyLong()))
                .thenReturn(true);

        Mockito.when(jobService.tryGetJob(Mockito.eq("1e3e0ddd-ac38-4929-bd0b-56dc001c4c5a")))
                .thenAnswer(_ -> Optional.of(jobSnapshotBefore))
                .thenAnswer(_ -> Optional.of(jobSnapshotAfter));

        // ------------------------------------
        ControllerHelper.maybeRedirectToJobData(
                jobService,
                response,
                "1e3e0ddd-ac38-4929-bd0b-56dc001c4c5a",
                "Wed, 21 Oct 2015 07:28:00 GMT",
                "HaikuDepot/0.0.8" // <-- note does not support retry when not ready.
        );
        // ------------------------------------

        Mockito.verify(jobService).awaitJobFinishedUninterruptibly(
                Mockito.eq("1e3e0ddd-ac38-4929-bd0b-56dc001c4c5a"),
                Mockito.anyLong());

        Assertions.assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_MOVED_TEMPORARILY);
        Assertions.assertThat(response.getHeader("Location"))
                .isEqualTo("/__secured/jobdata/91cf45b0-97ea-4cdf-a339-6f85ced6aa4f/download");
    }

    /**
     * The old HaikUDepot client does not support the server asking the client to try again later so
     * this logic will test that it tries to complete the job before returning and when it does not
     * complete the job in time, it will return a 503.
     */

    // TODO (andponlin) remove this once the older version of HaikuDepot is no longer supported.
    @Test
    public void testMaybeRedirectToJobData_notFinishedThenNotFinishedOlderVersion() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        JobService jobService = Mockito.mock(JobService.class);

        Job jobSnapshotBefore = new Job();
        jobSnapshotBefore.setStartTimestamp();
        // note; no finish timestamp so will retry
        jobSnapshotBefore.addGeneratedDataGuid("936e4d5a-8470-4fb0-8d94-647394e3cfd7");
        jobSnapshotBefore.setJobSpecification(anyJobSpecification());

//        Job jobSnapshotAfter = new Job();
//        jobSnapshotAfter.setQueuedTimestamp();
//        jobSnapshotAfter.setStartTimestamp();
//        // note; no finish timestamp so will fail
//        jobSnapshotAfter.addGeneratedDataGuid("91cf45b0-97ea-4cdf-a339-6f85ced6aa4f");
//        jobSnapshotAfter.setJobSpecification(anyJobSpecification());

        Mockito.when(jobService.tryGetJob(Mockito.eq("e969bc19-5ad2-4e9e-83f7-d06b20e4e89c")))
                .thenAnswer(_ -> Optional.of(jobSnapshotBefore));

        Mockito.when(jobService.awaitJobFinishedUninterruptibly(Mockito.eq("1e3e0ddd-ac38-4929-bd0b-56dc001c4c5a"), Mockito.anyLong()))
                .thenReturn(false); // <-- note; did not finish

        // ------------------------------------
        ControllerHelper.maybeRedirectToJobData(
                jobService,
                response,
                "e969bc19-5ad2-4e9e-83f7-d06b20e4e89c",
                "Wed, 21 Oct 2015 07:28:00 GMT",
                "HaikuDepot/0.0.8" // <-- note does not support retry when not ready.
        );
        // ------------------------------------

        Mockito.verify(jobService).awaitJobFinishedUninterruptibly(
                Mockito.eq("1e3e0ddd-ac38-4929-bd0b-56dc001c4c5a"),
                Mockito.anyLong());

        // signals to the client that the data was not generated in time.
        Assertions.assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        Assertions.assertThat(response.getHeader("Retry-After")).isEqualTo("5");
    }

    private JobSpecification anyJobSpecification() {
        JobSpecification specification = new PkgDumpExportJobSpecification();
        specification.setGuid("1e3e0ddd-ac38-4929-bd0b-56dc001c4c5a");
        return specification;
    }

}
