/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.model;

/**
 * <p>A category is something like Graphics, Education, etc... which a package is able to be classified into.</p>
 * @param code is the code of the Category that can be used to uniquely identify the category.
 * @param name is the human-readable name of the Category. It may be localized.
 */

public record PkgCategory(String code, String name) { }
