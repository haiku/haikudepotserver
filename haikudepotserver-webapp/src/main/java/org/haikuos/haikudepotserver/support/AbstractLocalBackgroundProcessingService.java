/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support;

import com.google.common.base.Preconditions;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.AbstractService;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class AbstractLocalBackgroundProcessingService extends AbstractService {

    public final static int SIZE_QUEUE = 10;

    protected ThreadPoolExecutor executor = null;

    protected ArrayBlockingQueue<Runnable> runnables = Queues.newArrayBlockingQueue(SIZE_QUEUE);

    @Override
    public void doStart() {
        try {
            Preconditions.checkState(null == executor);

            executor = new ThreadPoolExecutor(
                    0, // core pool size
                    1, // max pool size
                    1l, // time to shutdown threads
                    TimeUnit.MINUTES,
                    runnables,
                    new ThreadPoolExecutor.AbortPolicy());

            notifyStarted();
        }
        catch(Throwable th) {
            notifyFailed(th);
        }
    }

    @Override
    public void doStop() {
        try {
            Preconditions.checkNotNull(executor);
            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.MINUTES);
            executor = null;
            notifyStopped();
        }
        catch(Throwable th) {
            notifyFailed(th);
        }
    }

    public void startAsyncAndAwaitRunning() {
        startAsync();
        awaitRunning();
    }

    public void stopAsyncAndAwaitTerminated() {
        stopAsync();
        awaitTerminated();
    }

    /**
     * <p>Returns true if the service is actively working on a job or it has a job submitted which has not yet
     * been dequeued and run.</p>
     */

    public boolean isProcessingSubmittedJobs() {
        return
                null!=executor
                        && (executor.getActiveCount() > 0 || !executor.getQueue().isEmpty());
    }

}
