/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage;

import com.google.common.base.Suppliers;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.haiku.haikudepotserver.api2.MiscellaneousApiService;
import org.haiku.haikudepotserver.api2.RepositoryApiService;
import org.haiku.haikudepotserver.api2.model.*;
import org.haiku.haikudepotserver.multipage.model.*;
import org.haiku.haikudepotserver.multipage.model.Architecture;
import org.haiku.haikudepotserver.multipage.model.PkgCategory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * <p>Reference data is data that would be used across functionality in the system; Architectures is a good example.
 * Many pages will need Architectures and the data around Architectures is not something that would be typically
 * edited.</p>
 */

@Service
public class ReferenceDataRepository {

    private final static Duration CACHE_DURATION = Duration.ofMinutes(10);

    private final MiscellaneousApiService miscellaneousApiService;
    private final RepositoryApiService repositoryApiService;

    private final LoadingCache<String, ReferenceData> localizedReferenceData;
    private final Supplier<Defaults> defaultSupplier;

    public ReferenceDataRepository(
            MiscellaneousApiService miscellaneousApiService,
            RepositoryApiService repositoryApiService
    ) {
        this.miscellaneousApiService = miscellaneousApiService;
        this.repositoryApiService = repositoryApiService;

        localizedReferenceData = CacheBuilder.newBuilder()
                .expireAfterWrite(CACHE_DURATION)
                .build(new CacheLoader<>() {
                    @Override
                    public ReferenceData load(String naturalLanguageCode) {
                        return getReferenceDataWithoutCache(naturalLanguageCode);
                    }
                });

        defaultSupplier = Suppliers.memoizeWithExpiration(
                this::getDefaultsWithoutCache,
                CACHE_DURATION
        );
    }

    public ReferenceData getReferenceData(String naturalLanguageCode) {
        return localizedReferenceData.getUnchecked(naturalLanguageCode);
    }

    public Defaults getDefaults() {
        return defaultSupplier.get();
    }

    private ReferenceData getReferenceDataWithoutCache(String naturalLanguageCode) {
        GetAllArchitecturesRequestEnvelope getAllArchitecturesRequest = new GetAllArchitecturesRequestEnvelope();
        getAllArchitecturesRequest.setNaturalLanguageCode(naturalLanguageCode);
        List<org.haiku.haikudepotserver.api2.model.Architecture> architectures = miscellaneousApiService.getAllArchitectures(
                getAllArchitecturesRequest).getArchitectures();
        List<org.haiku.haikudepotserver.api2.model.PkgCategory> pkgCategories = miscellaneousApiService.getAllPkgCategories(
                new GetAllPkgCategoriesRequestEnvelope()).getPkgCategories();
        List<GetRepositoriesRepository> repositories = repositoryApiService.getRepositories(
                new GetRepositoriesRequestEnvelope()).getRepositories();

        return new ReferenceData(
                architectures.stream()
                        .map(a -> new Architecture(a.getCode()))
                        .sorted(Comparator.comparing(Architecture::code))
                        .toList(),
                pkgCategories.stream()
                        .map(pc -> new PkgCategory(pc.getCode(), pc.getName()))
                        .sorted(Comparator.comparing(PkgCategory::code))
                        .toList(),
                repositories.stream()
                        .map(r -> new Repository(r.getCode(), r.getName()))
                        .sorted(Comparator.comparing(Repository::code))
                        .toList()
        );
    }

    private String getDefaultRepositorySourceCode(String defaultArchitectureCode, String defaultRepositoryCode) {
        final GetRepositoryRequestEnvelope request = new GetRepositoryRequestEnvelope();
        request.setCode(defaultRepositoryCode);
        GetRepositoryResult response = repositoryApiService.getRepository(request);

        if (!response.getActive()) {
            throw new IllegalStateException("default repository [%s] is not active".formatted(defaultRepositoryCode));
        }

        return response.getRepositorySources()
                .stream()
                .filter(rs -> rs.getArchitectureCode().equals(defaultArchitectureCode))
                .findFirst()
                .map(GetRepositoryRepositorySource::getCode)
                .orElseThrow(() -> new IllegalStateException(
                        "default repository [%s] has no repository source with default architecture [%s]".formatted(
                                defaultRepositoryCode, defaultArchitectureCode)));
    }

    private Defaults getDefaultsWithoutCache() {
        final GetRuntimeInformationResult runtimeInformationResult = miscellaneousApiService.getRuntimeInformation();
        final String defaultArchitectureCode = runtimeInformationResult.getDefaults().getArchitectureCode();
        final String defaultRepositoryCode = runtimeInformationResult.getDefaults().getRepositoryCode();
        final String defaultRepositorySourceCode = getDefaultRepositorySourceCode(defaultArchitectureCode, defaultRepositoryCode);
        return new Defaults(
                defaultArchitectureCode,
                defaultRepositoryCode,
                defaultRepositorySourceCode
        );
    }

}
