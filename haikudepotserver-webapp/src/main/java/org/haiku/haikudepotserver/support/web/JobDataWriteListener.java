/*
 * Copyright 2016-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.web;

import com.google.common.io.ByteSource;
import org.haiku.haikudepotserver.job.controller.JobController;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * <p>This is used to stream data produced from a job out through a servlet 3.1 async response.</p>
 */

public class JobDataWriteListener implements WriteListener {

    private final static Logger LOGGER = LoggerFactory.getLogger(JobController.class);

    private final static int BUFFER_SIZE = 16 * 1024;

    private final String jobDataGuid;
    private final JobService jobService;
    private final AsyncContext async;
    private final ServletOutputStream outputStream;

    private int payloadOffset = 0;
    private final byte[] subPayloadBuffer = new byte[BUFFER_SIZE];

    public JobDataWriteListener(
            String jobDataGuid,
            JobService jobService,
            AsyncContext async,
            ServletOutputStream outputStream)
    {
        this.jobDataGuid = jobDataGuid;
        this.jobService = jobService;
        this.async = async;
        this.outputStream = outputStream;
    }

    @Override
    public void onWritePossible() throws IOException {
        Optional<JobDataWithByteSource> jobDataWithByteSourceOptional = jobService.tryObtainData(jobDataGuid);

        if (jobDataWithByteSourceOptional.isEmpty()) {
            LOGGER.error("unable to find the job data for; " + jobDataGuid);
            async.complete();
        } else {
            ByteSource byteSource = jobDataWithByteSourceOptional.get().getByteSource();

            while (payloadOffset >= 0 && outputStream.isReady()) {
                ByteSource subPayloadByteStream = byteSource.slice(payloadOffset, BUFFER_SIZE);
                int subPayloadBufferFillLength = readToBuffer(subPayloadByteStream);

                if (0 == subPayloadBufferFillLength) {
                    async.complete();
                    LOGGER.info("did complete async stream job data; {}", jobDataGuid);
                    payloadOffset = -1;
                } else {
                    outputStream.write(subPayloadBuffer, 0, subPayloadBufferFillLength);
                    payloadOffset += subPayloadBufferFillLength;
                }
            }
        }
    }

    private int readToBuffer(ByteSource byteSource) throws IOException {
        try (InputStream inputStream = byteSource.openStream()) {
            int lastBytesRead;
            int bufferUpto = 0;

            do {
                lastBytesRead = inputStream.read(subPayloadBuffer, bufferUpto, subPayloadBuffer.length - bufferUpto);
                bufferUpto += -1 != lastBytesRead ? lastBytesRead : 0;
            } while (bufferUpto < subPayloadBuffer.length && -1 != lastBytesRead);

            return bufferUpto;
        }
    }

    @Override
    public void onError(Throwable t) {
        LOGGER.error("an error has arisen writing async data; " + jobDataGuid, t);
        async.complete();
    }

}
