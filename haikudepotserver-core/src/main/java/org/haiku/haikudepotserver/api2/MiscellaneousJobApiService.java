package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api2.model.QueueReferenceDumpExportJobRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.QueueReferenceDumpExportJobResult;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.reference.model.ReferenceDumpExportJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("miscellaneousJobApiServiceV2")
public class MiscellaneousJobApiService extends AbstractApiService {

    protected static Logger LOGGER = LoggerFactory.getLogger(RepositoryJobApiService.class);

    private final ServerRuntime serverRuntime;

    private final JobService jobService;

    public MiscellaneousJobApiService(
            ServerRuntime serverRuntime,
            JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    public QueueReferenceDumpExportJobResult queueReferenceDumpExportJob(
            QueueReferenceDumpExportJobRequestEnvelope requestEnvelope
    ) {
        final ObjectContext context = serverRuntime.newContext();

        // as long as there is a user then it's OK to get this data

        User user = obtainAuthenticatedUser(context);

        // setup and go

        ReferenceDumpExportJobSpecification spec = new ReferenceDumpExportJobSpecification();
        spec.setOwnerUserNickname(user.getNickname());
        spec.setNaturalLanguageCode(requestEnvelope.getNaturalLanguageCode());

        return new QueueReferenceDumpExportJobResult()
                .guid(jobService.submit(spec, JobSnapshot.COALESCE_STATUSES_NONE));
    }

}
