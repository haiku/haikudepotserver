/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haikuos.haikudepotserver.api1.model.userrating;

public class GetUserRatingRequest {

    public String code;

    public GetUserRatingRequest() {
    }

    public GetUserRatingRequest(String code) {
        this.code = code;
    }
}
