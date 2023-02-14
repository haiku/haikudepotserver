/*
 * Copyright 2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.SimpleValidationFailure;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api1.model.captcha.GenerateCaptchaRequest;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgChangelogRequest;
import org.haiku.haikudepotserver.api1.model.pkg.IncrementViewCounterRequest;
import org.haiku.haikudepotserver.api1.model.user.AgreeUserUsageConditionsRequest;
import org.haiku.haikudepotserver.api1.model.user.AuthenticateUserRequest;
import org.haiku.haikudepotserver.api1.model.user.CreateUserRequest;
import org.haiku.haikudepotserver.api1.model.user.GetUserRequest;
import org.haiku.haikudepotserver.api1.model.user.GetUserUsageConditionsRequest;
import org.haiku.haikudepotserver.api1.model.userrating.CreateUserRatingRequest;
import org.haiku.haikudepotserver.api1.model.userrating.GetUserRatingByUserAndPkgVersionRequest;
import org.haiku.haikudepotserver.api1.model.userrating.SearchUserRatingsRequest;
import org.haiku.haikudepotserver.api1.model.userrating.UpdateUserRatingRequest;
import org.haiku.haikudepotserver.api1.support.Constants;
import org.haiku.haikudepotserver.support.exception.AuthorizationRuleConflictException;
import org.haiku.haikudepotserver.support.exception.BadPkgIconException;
import org.haiku.haikudepotserver.support.exception.CaptchaBadResponseException;
import org.haiku.haikudepotserver.support.exception.InvalidUserUsageConditionsException;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.haiku.haikudepotserver.support.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>HDS used an <a href="https://github.com/briandilley/jsonrpc4j">open-source library</a> for JSON-RPC
 * messaging between HD and HDS.  Unfortunately this library is not being actively maintained and it is
 * unlikely that it will reach compatibility with future changes in the underlying platform.</p>
 *
 * <p>Seeing this coming, in July 2021 the API used by HD moved over to use the widely used Open-API
 * standard with a quasi "REST-RPC" style API to replace the former "JSON-RPC" one.  The REST-RPC
 * API was feature-complete by July 2022 and the JSON-RPC functionality was subsequently reduced to
 * only that required to support older HD clients.</p>
 *
 * <p>By Feb 2023, around 97% of the traffic coming from HD clients using the newer "REST-RPC" API.  To
 * support the remaining 3% for the forseeable future without the open-source library, this controller
 * simulates enough of the older API required to support those clients.  In time, this controller and
 * the API1 should be removed.</p>
 */

@Controller
@Deprecated
public class CombinedApi1Controller {

    protected static final Logger LOGGER = LoggerFactory.getLogger(CombinedApi1Controller.class);

    private enum Type {
        CAPTCHA,
        PKG,
        USER,
        USER_RATING
    }

    private final ObjectMapper objectMapper;

    private final CaptchaApi captchaApi;

    private final PkgApi pkgApi;

    private final UserApi userApi;

    private final UserRatingApi userRatingApi;

    public CombinedApi1Controller(
            ObjectMapper objectMapper,
            CaptchaApi captchaApi,
            PkgApi pkgApi,
            UserApi userApi,
            UserRatingApi userRatingApi) {
        this.objectMapper = objectMapper;
        this.captchaApi = captchaApi;
        this.pkgApi = pkgApi;
        this.userApi = userApi;
        this.userRatingApi = userRatingApi;
    }

    @RequestMapping(
            value = {
                    "/__api/v1/pkg",
                    "/api/v1/pkg" // OLD LEGACY PATH
            },
            method = RequestMethod.POST)
    public ResponseEntity<JsonNode> pkgExec(@RequestBody JsonNode requestPayloadNode) {
        return ResponseEntity.ok(exec(Type.PKG, requestPayloadNode));
    }

    @RequestMapping(
            value = {
                    "/__api/v1/user",
                    "/api/v1/user" // OLD LEGACY PATH
            },
            method = RequestMethod.POST)
    public ResponseEntity<JsonNode> userExec(@RequestBody JsonNode requestPayloadNode) {
        return ResponseEntity.ok(exec(Type.USER, requestPayloadNode));
    }

    @RequestMapping(
            value = {
                    "/__api/v1/userrating",
                    "/api/v1/userrating" // OLD LEGACY PATH
            },
            method = RequestMethod.POST)
    public ResponseEntity<JsonNode> userRatingExec(@RequestBody JsonNode requestPayloadNode) {
        return ResponseEntity.ok(exec(Type.USER_RATING, requestPayloadNode));
    }

    private JsonNode exec(Type type, JsonNode requestBody) {
        try {
            String method = Optional.ofNullable(requestBody.get("method"))
                    .map(JsonNode::asText)
                    .map(StringUtils::trimToNull)
                    .orElse(null);

            if (null == method) {
                LOGGER.error("unable to establish the method from request envelope for {}", type);
                return toJsonRpcErrorPayload(new JsonRpcError(
                        Constants.ERROR_CODE_INVALID_REQUEST, "missing `method`"));
            }

            // JSON-RPC accepts a much wider number of possible request mechanisms, but
            // the older API only works with a POST request with a JSON payload.

            JsonNode param0Node = Optional.ofNullable(requestBody.get("params"))
                    .filter(JsonNode::isArray)
                    .filter(jn -> 1 == jn.size())
                    .map(jn -> jn.get(0))
                    .filter(JsonNode::isObject)
                    .orElse(null);

            if (null == param0Node) {
                LOGGER.error("unable to establish the parameter object from request envelope for {}#{}", type, method);
                return toJsonRpcErrorPayload(new JsonRpcError(
                        Constants.ERROR_CODE_INVALID_REQUEST, "missing `params`"));
            }

            // now need to figure out which method to invoke.

            return switch (type) {
                case CAPTCHA -> switch (method) {
                    case "generateCaptcha" -> toJsonRpcResponsePayload(
                            captchaApi.generateCaptcha(
                                objectMapper.treeToValue(param0Node, GenerateCaptchaRequest.class)));
                    default -> toJsonRpcErrorPayload(
                            new JsonRpcError(Constants.ERROR_CODE_METHOD_NOT_FOUND, method));
                };
                case PKG -> switch (method) {
                    case "getPkgChangelog" -> toJsonRpcResponsePayload(
                            pkgApi.getPkgChangelog(
                                    objectMapper.treeToValue(
                                            param0Node, GetPkgChangelogRequest.class)));
                    case "incrementViewCounter" -> toJsonRpcResponsePayload(
                            pkgApi.incrementViewCounter(
                                    objectMapper.treeToValue(
                                            param0Node, IncrementViewCounterRequest.class)));
                    default -> toJsonRpcErrorPayload(
                            new JsonRpcError(Constants.ERROR_CODE_METHOD_NOT_FOUND, method));
                };
                case USER -> switch (method) {
                    case "createUser" -> toJsonRpcResponsePayload(
                            userApi.createUser(objectMapper.treeToValue(
                                    param0Node, CreateUserRequest.class)));
                    case "getUser" -> toJsonRpcResponsePayload(
                            userApi.getUser(objectMapper.treeToValue(
                                    param0Node, GetUserRequest.class)));
                    case "authenticateUser" -> toJsonRpcResponsePayload(
                            userApi.authenticateUser(objectMapper.treeToValue(
                                    param0Node, AuthenticateUserRequest.class)));
                    case "getUserUsageConditions" -> toJsonRpcResponsePayload(
                            userApi.getUserUsageConditions(objectMapper.treeToValue(
                                    param0Node, GetUserUsageConditionsRequest.class)));
                    case "agreeUserUsageConditions" -> toJsonRpcResponsePayload(
                            userApi.agreeUserUsageConditions(objectMapper.treeToValue(
                                    param0Node, AgreeUserUsageConditionsRequest.class)));
                    default -> toJsonRpcErrorPayload(
                            new JsonRpcError(Constants.ERROR_CODE_METHOD_NOT_FOUND, method));
                };

                case USER_RATING -> switch (method) {
                    case "getUserRatingByUserAndPkgVersion" -> toJsonRpcResponsePayload(
                            userRatingApi.getUserRatingByUserAndPkgVersion(objectMapper.treeToValue(
                                    param0Node, GetUserRatingByUserAndPkgVersionRequest.class)));
                    case "createUserRating" -> toJsonRpcResponsePayload(
                            userRatingApi.createUserRating(objectMapper.treeToValue(
                                    param0Node, CreateUserRatingRequest.class)));
                    case "updateUserRating" -> toJsonRpcResponsePayload(
                            userRatingApi.updateUserRating(objectMapper.treeToValue(
                                    param0Node, UpdateUserRatingRequest.class)));
                    case "searchUserRatings" -> toJsonRpcResponsePayload(
                            userRatingApi.searchUserRatings(objectMapper.treeToValue(
                                    param0Node, SearchUserRatingsRequest.class)));
                    default -> toJsonRpcErrorPayload(
                            new JsonRpcError(Constants.ERROR_CODE_METHOD_NOT_FOUND, method));
                };
            };
        }
        catch (ObjectNotFoundException onfe) {
            LOGGER.info("object not found invoking {} api; {}:{} ", type, onfe.getEntityName(), onfe.getIdentifier());
            return toJsonRpcErrorPayload(toJsonRpcError(onfe));
        }
        catch (Throwable t) {
            LOGGER.error("error invoking {} api1 (legacy)", type, t);
            return toJsonRpcErrorPayload(toJsonRpcError(t));
        }
    }

    private ObjectNode toJsonRpcResponsePayload(Object object) {
        ObjectNode result = objectMapper.createObjectNode();
        result.set("result", objectMapper.convertValue(object, JsonNode.class));
        return result;
    }

    private ObjectNode toJsonRpcErrorPayload(JsonRpcError error) {
        ObjectNode result = objectMapper.createObjectNode();
        result.set("error", objectMapper.convertValue(error, JsonNode.class));
        return result;
    }

    private JsonRpcError toJsonRpcError(Throwable t) {

        if (t instanceof InvalidUserUsageConditionsException) {
            return new JsonRpcError(
                    Constants.ERROR_CODE_INVALID_USER_USAGE_CONDITIONS,
                    "invaliduserusageconditions",
                    null);
        }

        if (AuthorizationRuleConflictException.class.isAssignableFrom(t.getClass())) {
            return new JsonRpcError(
                    Constants.ERROR_CODE_AUTHORIZATIONRULECONFLICT,
                    "authorizationruleconflict",
                    null);
        }

        // output for authorization failure.

        if (AccessDeniedException.class.isAssignableFrom(t.getClass())) {
            return new JsonRpcError(
                    Constants.ERROR_CODE_AUTHORIZATIONFAILURE,
                    "authorizationfailure",
                    null);
        }

        // special output for a bad captcha

        if (CaptchaBadResponseException.class.isAssignableFrom(t.getClass())) {
            return new JsonRpcError(
                    Constants.ERROR_CODE_CAPTCHABADRESPONSE,
                    "captchabadresponse",
                    null);
        }

        if (BadPkgIconException.class.isAssignableFrom(t.getClass())) {
            BadPkgIconException badPkgIconException = (BadPkgIconException) t;

            Map<String,Object> errorData = Maps.newHashMap();
            errorData.put("mediaTypeCode", badPkgIconException.getMediaTypeCode());

            if(null != badPkgIconException.getSize()) {
                errorData.put("size", badPkgIconException.getSize());
            }

            return new JsonRpcError(
                    Constants.ERROR_CODE_BADPKGICON,
                    "badpkgicon",
                    errorData);
        }

        // special output for the object not found exceptions

        if(ObjectNotFoundException.class.isAssignableFrom(t.getClass())) {
            ObjectNotFoundException objectNotFoundException = (ObjectNotFoundException) t;

            return new JsonRpcError(
                    Constants.ERROR_CODE_OBJECTNOTFOUND,
                    "objectnotfound",
                    ImmutableMap.of(
                            "entityName", objectNotFoundException.getEntityName(),
                            "identifier", objectNotFoundException.getIdentifier()
                    )
            );
        }

        // special output for the validation exceptions

        if(ValidationException.class.isAssignableFrom(t.getClass())) {
            ValidationException validationException = (ValidationException) t;

            return new JsonRpcError(
                    Constants.ERROR_CODE_VALIDATION,
                    "validationerror",
                    Collections.singletonMap(
                            "validationfailures",
                            validationException.getValidationFailures()
                                    .stream()
                                    .map(vf -> ImmutableMap.of(
                                            "property",vf.getProperty(),
                                            "message",vf.getMessage()
                                    ))
                                    .collect(Collectors.toList())
                    )
            );
        }

        // special output for cayenne validation exceptions

        if(org.apache.cayenne.validation.ValidationException.class.isAssignableFrom(t.getClass())) {
            org.apache.cayenne.validation.ValidationException validationException = (org.apache.cayenne.validation.ValidationException) t;

            return new JsonRpcError(
                    Constants.ERROR_CODE_VALIDATION,
                    "validationerror",
                    Collections.singletonMap(
                            "validationfailures",
                            validationException.getValidationResult().getFailures()
                                    .stream()
                                    .map(f -> {
                                        if (BeanValidationFailure.class.isAssignableFrom(f.getClass())) {
                                            BeanValidationFailure beanValidationFailure = (BeanValidationFailure) f;
                                            Object err = beanValidationFailure.getError();
                                            return ImmutableMap.of(
                                                    "property", beanValidationFailure.getProperty(),
                                                    "message", null != err ? err.toString() : "");
                                        }

                                        if (SimpleValidationFailure.class.isAssignableFrom(f.getClass())) {
                                            SimpleValidationFailure simpleValidationFailure = (SimpleValidationFailure) f;
                                            return ImmutableMap.of(
                                                    "property", "",
                                                    "message", simpleValidationFailure.getDescription());
                                        }

                                        throw new IllegalStateException("unable to establish data portion of validation exception owing to unknown cayenne validation failure; " + f.getClass().getSimpleName());
                                    })
                                    .collect(Collectors.toList())
                    )
            );
        }

        return new JsonRpcError(
                Constants.ERROR_CODE_NOT_HANDLED,
                "error not handled");
    }

    /**
     * <p>This object models the error that gets returned to clients when
     * something goes wrong.</p>
     */

    private static class JsonRpcError {
        private final int code;
        private final String message;
        private final Object data;

        public JsonRpcError(int code, String message) {
            this(code, message, null);
        }

        public JsonRpcError(int code, String message, Object data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public Object getData() {
            return data;
        }
    }

}
