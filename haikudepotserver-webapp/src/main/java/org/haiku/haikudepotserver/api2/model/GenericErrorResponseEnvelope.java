/*
 * Copyright 2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2.model;

import org.apache.commons.collections4.MapUtils;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>This mirrors the structure in the <code>error.yaml</code> for API version 2.</p>
 */

public class GenericErrorResponseEnvelope {

    private final Error error;

    public GenericErrorResponseEnvelope(int code, String message) {
        this(code, message, Map.of());
    }

    public GenericErrorResponseEnvelope(int code, String message, Map<String, String> data) {
        this.error = new Error();
        this.error.setCode(code);
        this.error.setMessage(message);
        this.error.setData(MapUtils
                .emptyIfNull(data)
                .entrySet()
                .stream()
                .map(e -> {
                    ErrorData errorData = new ErrorData();
                    errorData.setKey(e.getKey());
                    errorData.setValue(e.getValue());
                    return errorData;
                })
                .collect(Collectors.toUnmodifiableList()));
    }

    public Error getError() {
        return error;
    }

}
