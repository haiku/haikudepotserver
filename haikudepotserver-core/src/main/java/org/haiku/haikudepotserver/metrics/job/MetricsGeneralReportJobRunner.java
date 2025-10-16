/*
 * Copyright 2023-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.metrics.job;

import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.*;
import org.haiku.haikudepotserver.metrics.MetricsConstants;
import org.haiku.haikudepotserver.metrics.model.MetricsGeneralReportJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * <p>This produces a report which outlines in text some of the key
 * metrics of the running application.</p>
 */

@Component
public class MetricsGeneralReportJobRunner extends AbstractJobRunner<MetricsGeneralReportJobSpecification> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(MetricsGeneralReportJobRunner.class);

    private final MeterRegistry meterRegistry;

    public MetricsGeneralReportJobRunner(MeterRegistry meterRegistry) {
        this.meterRegistry = Preconditions.checkNotNull(meterRegistry);
    }

    @Override
    public Class<MetricsGeneralReportJobSpecification> getSupportedSpecificationClass() {
        return MetricsGeneralReportJobSpecification.class;
    }

    @Override
    public void run(JobService jobService, MetricsGeneralReportJobSpecification specification) throws IOException, JobRunnerException {
        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.JSON_UTF_8.toString(),
                JobDataEncoding.GZIP);

        try (
                final OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                final Writer outputStreamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
        ) {
            write(outputStreamWriter);
        }
    }

    private void write(Writer writer) throws IOException {

        writer.write("Desktop Versions Traffic\n");
        meterRegistry.find(MetricsConstants.COUNTER_NAME_DESKTOP_REQUESTS)
                .counters()
                .forEach(c -> writeCounter(
                        writer,
                        c.getId().getTag(MetricsConstants.TAG_NAME_VERSION),
                        (long) c.count()));
        writer.write('\n');

        writer.write("API Generation\n");
        Stream.of("v1", "v2")
                .forEach(v -> writeCounter(writer, v,
                        countHttpRequestsByPath(t -> t.startsWith("/__api/" + v + '/'))));
        writer.write('\n');

        writer.write("Multi-/Single-page\n");
        writeCounter(writer, "Single", countHttpRequestsByPath(t -> t.equals("/")));
        writeCounter(writer, "Multi", countHttpRequestsByPath(t -> t.startsWith("/__multipage")));
        writer.write('\n');

        writer.write("Bulk Downloads\n");
        Stream.of(
                "/__reference/all-",
                "/__repository/all-",
                "/__repository/all-",
                "/__pkg/all-",
                "/__pkgicon/all-"
        ).forEach(x -> writeCounter(writer, x + "**", countHttpRequestsByPath(t -> t.startsWith(x))));
        writer.write('\n');

        writer.write("Screenshots\n");
        writeCounter(writer, "/__pkgscreenshot/**", countHttpRequestsByPath(t -> t.startsWith("/__pkgscreenshot/")));
        writer.write('\n');
    }

    private long countHttpRequestsByPath(Predicate<String> pathPredicate) {
        return countHttpRequestsByPathAndMethod(pathPredicate, null);
    }

    private long countHttpRequestsByPathAndMethod(Predicate<String> pathPredicate, String method) {
        return meterRegistry.find("http.server.requests")
                .timers()
                .stream()
                .filter(t -> Optional.ofNullable(t.getId().getTag("uri"))
                        .filter(pathPredicate)
                        .isPresent())
                .filter(t -> null == method || Optional.ofNullable(t.getId().getTag("method"))
                        .filter(m -> StringUtils.equalsIgnoreCase(m, method))
                        .isPresent())
                .mapToLong(Timer::count)
                .sum();
    }

    private void writeCounter(Writer writer, String name, long count)  {
        try {
            writer.write(StringUtils.rightPad(name, 96, " ."));
            writer.write(" ");
            writer.write(Long.toString(count));
            writer.write("\n");
        }
        catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

}
