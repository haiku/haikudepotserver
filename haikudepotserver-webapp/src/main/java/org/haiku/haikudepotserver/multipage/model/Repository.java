/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.model;

/**
 * <p>A repository is a logical source of packages. The repository does not specify the actual transport media for the
 * package data; this is implied by a repository-source.</p>
 * @param code uniquely identifies the repository.
 * @param name is a human-readable name for the repository. This value may be localized.
 */

public record Repository(String code, String name) {}
