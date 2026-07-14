/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.model;

import jakarta.annotation.Nullable;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record ReferenceData(
      List<Architecture> architectures,
      List<PkgCategory> pkgCategories,
      List<Repository> repositories
) {

    private final static Set<String> PSEUDO_ARCHITECTURE_CODES = Set.of(
            "any",
            "source"
    );

    public List<Architecture> architecturesExcludingPseudo() {
        return architectures
                .stream()
                .filter(a -> !PSEUDO_ARCHITECTURE_CODES.contains(a.code()))
                .toList();
    }

    public Optional<Architecture> tryArchitectureForCode(String code) {
        return architectures
                .stream()
                .filter(a -> a.code().equals(code))
                .findFirst();
    }

    public Architecture architectureForCode(String code) {
        return tryArchitectureForCode(code)
                .orElseThrow(() -> new ObjectNotFoundException(Architecture.class.getSimpleName(), code));
    }

    public List<PkgCategory> pkgCategoriesForCodes(Collection<String> codes) {
        return pkgCategories
                .stream()
                .filter(c -> codes.contains(c.code()))
                .toList();
    }

    public PkgCategory pkgCategoryForCode(String code) {
        return pkgCategories
                .stream()
                .filter(a -> a.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new ObjectNotFoundException(PkgCategory.class.getSimpleName(), code));
    }

    public Repository repositoryForCode(String code) {
        return repositories
                .stream()
                .filter(a -> a.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new ObjectNotFoundException(PkgCategory.class.getSimpleName(), code));
    }

    public List<Repository> repositoriesForCodes(@Nullable Collection<String> codes) {
        return repositories
                .stream()
                .filter(a -> null != codes && codes.contains(a.code()))
                .toList();
    }

}


