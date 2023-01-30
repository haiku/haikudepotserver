/*
 * Copyright 2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.api2.model.QueueMetricsGeneralReportJobResponseEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class MetricsJobApiImpl implements MetricsJobApi {

    private MetricsJobApiService metricsJobApiService;

    public MetricsJobApiImpl(MetricsJobApiService metricsJobApiService) {
        this.metricsJobApiService = Preconditions.checkNotNull(metricsJobApiService);
    }

    @Override
    public ResponseEntity<QueueMetricsGeneralReportJobResponseEnvelope> queueMetricsGeneralReportJob(Object body) {
        return ResponseEntity.ok(
                new QueueMetricsGeneralReportJobResponseEnvelope()
                .result(metricsJobApiService.queueMetricsGeneralReportJob()));
    }

}
