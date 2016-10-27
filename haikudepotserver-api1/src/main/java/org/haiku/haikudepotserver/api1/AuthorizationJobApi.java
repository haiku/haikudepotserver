/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haiku.haikudepotserver.api1.model.authorization.job.QueueAuthorizationRulesSpreadsheetRequest;
import org.haiku.haikudepotserver.api1.model.authorization.job.QueueAuthorizationRulesSpreadsheetResult;

@JsonRpcService("/__api/v1/authorization/job")
public interface AuthorizationJobApi {

    /**
     * @since 2016-10-24
     */
    QueueAuthorizationRulesSpreadsheetResult queueAuthorizationRulesSpreadsheet(QueueAuthorizationRulesSpreadsheetRequest request);

}
