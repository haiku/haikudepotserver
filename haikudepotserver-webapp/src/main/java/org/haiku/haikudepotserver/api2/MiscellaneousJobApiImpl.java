package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.api2.model.QueueReferenceDumpExportJobRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.QueueReferenceDumpExportJobResponseEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class MiscellaneousJobApiImpl extends AbstractApiImpl implements MiscellaneousJobApi {

    private final MiscellaneousJobApiService miscellaneousJobApiService;

    public MiscellaneousJobApiImpl(MiscellaneousJobApiService miscellaneousJobApiService) {
        this.miscellaneousJobApiService = Preconditions.checkNotNull(miscellaneousJobApiService);
    }

    @Override
    public ResponseEntity<QueueReferenceDumpExportJobResponseEnvelope> queueReferenceDumpExportJob(QueueReferenceDumpExportJobRequestEnvelope queueReferenceDumpExportJobRequestEnvelope) {
        return ResponseEntity.ok(
                new QueueReferenceDumpExportJobResponseEnvelope().result(
                        miscellaneousJobApiService.queueReferenceDumpExportJob(queueReferenceDumpExportJobRequestEnvelope)
                )
        );
    }

}
