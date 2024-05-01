/*
 * Copyright 2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api2.model.QueueMetricsGeneralReportJobJobResult;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.metrics.model.MetricsGeneralReportJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component("metricsJobApiServiceV2")
public class MetricsJobApiService extends AbstractApiService {

    protected final static Logger LOGGER = LoggerFactory.getLogger(MetricsJobApiService.class);

    private final ServerRuntime serverRuntime;
    private final JobService jobService;

    public MetricsJobApiService(ServerRuntime serverRuntime, JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    public QueueMetricsGeneralReportJobJobResult queueMetricsGeneralReportJob() {

        final ObjectContext context = serverRuntime.newContext();
        User user = obtainAuthenticatedUser(context);

        if (null == user) {
            throw new AccessDeniedException("need a user to run the metrics general report");
        }

        // no need to worry about users here -- nothing sensitive.

        AbstractJobSpecification spec = new MetricsGeneralReportJobSpecification();
        spec.setOwnerUserNickname(user.getNickname());
        String guid = jobService.submit(spec, JobSnapshot.COALESCE_STATUSES_NONE);

        return new QueueMetricsGeneralReportJobJobResult().guid(guid);
    }

}
