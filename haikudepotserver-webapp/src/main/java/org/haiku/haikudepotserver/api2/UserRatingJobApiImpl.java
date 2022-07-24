/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.api2.model.QueueUserRatingSpreadsheetJobRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.QueueUserRatingSpreadsheetJobResponseEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class UserRatingJobApiImpl extends AbstractApiImpl implements UserRatingJobApi {

    private final UserRatingJobApiService userRatingJobApiService;

    public UserRatingJobApiImpl(UserRatingJobApiService userRatingJobApiService) {
        this.userRatingJobApiService = Preconditions.checkNotNull(userRatingJobApiService);
    }

    @Override
    public ResponseEntity<QueueUserRatingSpreadsheetJobResponseEnvelope> queueUserRatingSpreadsheetJob(QueueUserRatingSpreadsheetJobRequestEnvelope queueUserRatingSpreadsheetJobRequestEnvelope) {
        userRatingJobApiService.queueUserRatingSpreadsheetJob(queueUserRatingSpreadsheetJobRequestEnvelope);
        return ResponseEntity.ok(new QueueUserRatingSpreadsheetJobResponseEnvelope().result(
                userRatingJobApiService.queueUserRatingSpreadsheetJob(queueUserRatingSpreadsheetJobRequestEnvelope)));
    }
}
