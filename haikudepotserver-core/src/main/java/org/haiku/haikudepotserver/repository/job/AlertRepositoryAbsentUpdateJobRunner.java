package org.haiku.haikudepotserver.repository.job;

import com.google.common.base.Preconditions;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.repository.model.AlertRepositoryAbsentUpdateJobSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AlertRepositoryAbsentUpdateJobRunner extends AbstractJobRunner<AlertRepositoryAbsentUpdateJobSpecification> {

    private final ServerRuntime serverRuntime;

    private final RepositoryService repositoryService;

    public AlertRepositoryAbsentUpdateJobRunner(
            ServerRuntime serverRuntime,
            RepositoryService repositoryService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.repositoryService = Preconditions.checkNotNull(repositoryService);
    }

    @Override
    public void run(JobService jobService, AlertRepositoryAbsentUpdateJobSpecification specification) throws IOException, JobRunnerException {
        repositoryService.alertForRepositoriesAbsentUpdates(serverRuntime.newContext());
    }

}
