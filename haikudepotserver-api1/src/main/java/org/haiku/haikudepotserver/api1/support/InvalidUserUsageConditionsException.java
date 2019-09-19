/*
 * Copyright 2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.support;

/**
 * <p>This exception is thrown in the situation where a user usage conditions
 * is required (such as signing up) but the conditions that are presented have
 * already expired, are missing or are out of date.  They are not the latest.
 * </p>
 */

public class InvalidUserUsageConditionsException extends Exception {
}
