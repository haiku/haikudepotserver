/*
 * Copyright 2025-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.eventing;

import com.google.common.util.concurrent.Uninterruptibles;
import jakarta.annotation.Resource;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.job.model.JobAvailableEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>This test tests the processing of events through postgres.</p>
 */

@Execution(ExecutionMode.SAME_THREAD)
@ContextConfiguration(classes = { TestConfig.class, InterProcessEventPgIT.TestEventListener.class })
// Because of the addition of the `TestEventListener`, a new context is started for this test. By default,
// the context is not torn down until the whole test process has completed which causes a problem because
// then there may be two of some beans working off the same database. To avoid this, we mark the class
// as being one that dirties the context and then the context is shutdown which will tear down any
// spurious instances.
@DirtiesContext
public class InterProcessEventPgIT extends AbstractIntegrationTest {

    @Resource
    private TestEventListener eventListener;

    @Resource
    private InterProcessEventPgNotifyService notifyService;

    /**
     * <p>This test will artificially publish a message and check that it comes in.</p>
     */
    @Test
    public void testPgNotifyAndListen_fromOther() {
        eventListener.clear();

        // by passing a source identifier as "OTHER" the receiver will not think it is coming from the same application
        // instance and so will process it.
        JobAvailableEvent event = new JobAvailableEvent();
        event.setSourceIdentifier("OTHER");
        notifyService.publishEvent(event);

        Awaitility.waitAtMost(4, TimeUnit.SECONDS).until(() -> !eventListener.getCapturedEvents().isEmpty());
    }

    /**
     * <p>This test will publish a message and because it is coming from the same application, will not publish it.</p>
     */
    @Test
    public void testPgNotifyAndListen_fromSame() {
        eventListener.clear();

        notifyService.publishEvent(new JobAvailableEvent());

        Uninterruptibles.sleepUninterruptibly(4, TimeUnit.SECONDS);

        Assertions.assertThat(eventListener.getCapturedEvents().isEmpty()).isTrue();
    }

    @Component
    static class TestEventListener {

        private final Lock lock = new ReentrantLock();

        private final List<JobAvailableEvent> capturedEvents = new ArrayList<>();

        @EventListener
        public void onApplicationEvent(JobAvailableEvent event) {
            try {
                lock.lock();
                capturedEvents.add(event);
            } finally {
                lock.unlock();
            }
        }

        public void clear() {
            try {
                lock.lock();
                capturedEvents.clear();
            } finally {
                lock.unlock();
            }
        }

        public List<JobAvailableEvent> getCapturedEvents() {
            try {
                lock.lock();
                return List.copyOf(capturedEvents);
            } finally {
                lock.unlock();
            }
        }

    }

}
