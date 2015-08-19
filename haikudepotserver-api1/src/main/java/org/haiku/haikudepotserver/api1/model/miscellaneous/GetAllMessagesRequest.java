/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.miscellaneous;

public class GetAllMessagesRequest {

    public String naturalLanguageCode;

    public GetAllMessagesRequest() {
    }

    public GetAllMessagesRequest(String naturalLanguageCode) {
        this.naturalLanguageCode = naturalLanguageCode;
    }

}
