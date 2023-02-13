package org.haiku.haikudepotserver.metrics.job;

import com.google.common.base.Charsets;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.metrics.model.MetricsGeneralReportJobSpecification;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@ContextConfiguration(classes = TestConfig.class)
public class MetricsGeneralReportJobRunnerIT extends AbstractIntegrationTest {

    @Resource
    private JobService jobService;

    @Resource
    private MeterRegistry meterRegistry;

    @Test
    public void testRun() throws IOException {

        setupMetrics();
        MetricsGeneralReportJobSpecification spec = new MetricsGeneralReportJobSpecification();

        // ------------------------------------
        String guid = jobService.submit(
                spec,
                JobSnapshot.COALESCE_STATUSES_NONE);
        // ------------------------------------

        jobService.awaitJobFinishedUninterruptibly(guid, 10000);
        Optional<? extends JobSnapshot> snapshotOptional = jobService.tryGetJob(guid);
        Assertions.assertThat(snapshotOptional.get().getStatus()).isEqualTo(JobSnapshot.Status.FINISHED);

        String dataGuid = snapshotOptional.get().getGeneratedDataGuids()
                .stream()
                .collect(SingleCollector.single());
        JobDataWithByteSource jobSource = jobService.tryObtainData(dataGuid).get();
        String[] contentLines = new String(jobSource.getByteSource().read(), Charsets.UTF_8).split("[\\n\\r]+");

        Stream.of(
                "^1\\.2\\.3.+1$",
                        "^v1.+1$",
                        "^v2.+1$",
                        "^Single.+1$",
                        "^Multi.+1$")
                .map(Pattern::compile)
                .forEach(p -> Assertions
                        .assertThat(Stream.of(contentLines).anyMatch(cl -> p.matcher(cl).matches()))
                        .isTrue());
    }

    private void setupMetrics() {
        meterRegistry.counter("hds.desktop.requests", Set.of(Tag.of("version", "1.2.3"))).increment(1);
        meterRegistry.timer("http.server.requests", Set.of(Tag.of("uri", "/__api/v1/abc"))).record(1000, TimeUnit.SECONDS);
        meterRegistry.timer("http.server.requests", Set.of(Tag.of("uri", "/__api/v2/abc"))).record(1000, TimeUnit.SECONDS);
        meterRegistry.timer("http.server.requests", Set.of(Tag.of("uri", "/"))).record(1000, TimeUnit.SECONDS);
        meterRegistry.timer("http.server.requests", Set.of(Tag.of("uri", "/__multipage/xyz"))).record(1000, TimeUnit.SECONDS);
        meterRegistry.timer("http.server.requests", Set.of(Tag.of("uri", "/__repository/all-en.json.gz"))).record(1000, TimeUnit.SECONDS);
        meterRegistry.timer("http.server.requests", Set.of(Tag.of("uri", "/__pkgscreenshot/aaaaaaaa.png"))).record(1000, TimeUnit.SECONDS);
    }

}
