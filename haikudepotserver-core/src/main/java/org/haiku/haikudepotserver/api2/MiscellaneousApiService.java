/*
 * Copyright 2022-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api2.model.Contributor;
import org.haiku.haikudepotserver.api2.model.ContributorType;
import org.haiku.haikudepotserver.api2.model.GenerateFeedUrlRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GenerateFeedUrlResult;
import org.haiku.haikudepotserver.api2.model.GetAllArchitecturesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetAllArchitecturesResult;
import org.haiku.haikudepotserver.api2.model.GetAllContributorsResult;
import org.haiku.haikudepotserver.api2.model.GetAllCountriesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetAllCountriesResult;
import org.haiku.haikudepotserver.api2.model.GetAllMessagesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetAllMessagesResult;
import org.haiku.haikudepotserver.api2.model.GetAllNaturalLanguagesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetAllNaturalLanguagesResult;
import org.haiku.haikudepotserver.api2.model.GetAllPkgCategoriesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetAllPkgCategoriesResult;
import org.haiku.haikudepotserver.api2.model.GetAllProminencesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetAllProminencesResult;
import org.haiku.haikudepotserver.api2.model.GetAllUserRatingStabilitiesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetAllUserRatingStabilitiesResult;
import org.haiku.haikudepotserver.api2.model.GetRuntimeInformationResult;
import org.haiku.haikudepotserver.api2.model.GetRuntimeInformationResultDefaults;
import org.haiku.haikudepotserver.api2.model.Message;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.Country;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgCategory;
import org.haiku.haikudepotserver.dataobjects.Prominence;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.UserRatingStability;
import org.haiku.haikudepotserver.dataobjects.auto._User;
import org.haiku.haikudepotserver.feed.model.FeedService;
import org.haiku.haikudepotserver.feed.model.FeedSpecification;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.support.ContributorsService;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@Component("miscellaneousApiServiceV2")
public class MiscellaneousApiService extends AbstractApiService {

    protected static Logger LOGGER = LoggerFactory.getLogger(MiscellaneousApiService.class);

    private final ServerRuntime serverRuntime;
    private final RuntimeInformationService runtimeInformationService;
    private final FeedService feedService;
    private final ContributorsService contributorsService;
    private final MessageSource messageSource;
    private final NaturalLanguageService naturalLanguageService;
    private final Boolean isProduction;
    private final String architectureDefaultCode;
    private final String repositoryDefaultCode;

    @Autowired
    public MiscellaneousApiService(
            ServerRuntime serverRuntime,
            RuntimeInformationService runtimeInformationService,
            FeedService feedService,
            ContributorsService contributorsService,
            MessageSource messageSource,
            NaturalLanguageService naturalLanguageService,
            @Value("${hds.deployment.is-production:false}") Boolean isProduction,
            @Value("${hds.architecture.default.code}") String architectureDefaultCode,
            @Value("${hds.repository.default.code}") String repositoryDefaultCode) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.runtimeInformationService = Preconditions.checkNotNull(runtimeInformationService);
        this.feedService = Preconditions.checkNotNull(feedService);
        this.contributorsService = Preconditions.checkNotNull(contributorsService);
        this.messageSource = Preconditions.checkNotNull(messageSource);
        this.naturalLanguageService = Preconditions.checkNotNull(naturalLanguageService);
        this.isProduction = Preconditions.checkNotNull(isProduction);
        this.architectureDefaultCode = Preconditions.checkNotNull(architectureDefaultCode);
        this.repositoryDefaultCode = Preconditions.checkNotNull(repositoryDefaultCode);
    }

    public GenerateFeedUrlResult generateFeedUrl(GenerateFeedUrlRequestEnvelope request) {
        Preconditions.checkNotNull(request);

        final ObjectContext context = serverRuntime.newContext();
        FeedSpecification specification = new FeedSpecification();
        specification.setFeedType(FeedSpecification.FeedType.ATOM);
        specification.setLimit(request.getLimit());

        if (null != request.getSupplierTypes()) {
            specification.setSupplierTypes(
                    request.getSupplierTypes()
                            .stream()
                            .map(st -> FeedSpecification.SupplierType.valueOf(st.name()))
                            .toList()
            );
        }

        if (null != request.getNaturalLanguageCode()) {
            specification.setNaturalLanguageCoordinates(NaturalLanguageCoordinates.fromCode(request.getNaturalLanguageCode()));
        }

        if (null != request.getPkgNames()) {
            specification.setPkgNames(
                    request.getPkgNames()
                            .stream()
                            .map(pn -> Pkg.tryGetByName(context, pn))
                            .filter(Optional::isPresent)
                            .map(p -> p.get().getName())
                            .toList()
            );
        }

        return new GenerateFeedUrlResult().url(feedService.generateUrl(specification));
    }

    public GetAllArchitecturesResult getAllArchitectures(GetAllArchitecturesRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        return new GetAllArchitecturesResult()
                .architectures(Architecture.getAll(serverRuntime.newContext())
                        .stream()
                        .filter(a -> !a.getCode().equals(Architecture.CODE_SOURCE) && !a.getCode().equals(Architecture.CODE_ANY))
                        .map(a -> new org.haiku.haikudepotserver.api2.model.Architecture().code(a.getCode()))
                        .toList());
    }

    public GetAllContributorsResult getAllContributors() {
        return new GetAllContributorsResult()
                .contributors(contributorsService.getConstributors().stream()
                        .map(c -> new Contributor()
                                .type(ContributorType.valueOf(c.getType().name()))
                                .name(c.getName())
                                .naturalLanguageCode(c.getNaturalLanguageCode())
                        )
                        .toList());
    }

    public GetAllCountriesResult getAllCountries(GetAllCountriesRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        final ObjectContext context = serverRuntime.newContext();
        return new GetAllCountriesResult()
                .countries(Country.getAll(context)
                        .stream()
                        .map(c -> new org.haiku.haikudepotserver.api2.model.Country()
                                .code(c.getCode())
                                .name(c.getName()))
                        .toList());
    }

    public GetAllMessagesResult getAllMessages(GetAllMessagesRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(request.getNaturalLanguageCode());

        ObjectContext context = serverRuntime.newContext();
        NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.getNaturalLanguageCode());
        Properties allLocalizationMessages = naturalLanguageService.getAllLocalizationMessages(naturalLanguage.toCoordinates());

        return new GetAllMessagesResult()
                .messages(allLocalizationMessages.keySet().stream()
                        .map(k -> new Message()
                                .key(k.toString())
                                .value(allLocalizationMessages.get(k).toString()))
                        .collect(Collectors.toList()));
    }


    public GetAllNaturalLanguagesResult getAllNaturalLanguages(GetAllNaturalLanguagesRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        final ObjectContext context = serverRuntime.newContext();

        final NaturalLanguage naturalLanguage =
                Optional.ofNullable(request.getNaturalLanguageCode())
                        .filter(StringUtils::isNotBlank)
                        .map(c -> getNaturalLanguage(context, c))
                        .orElse(null);

        final Set<NaturalLanguageCoordinates> natLangCoordsWithData = naturalLanguageService.findNaturalLanguagesWithData();
        final Set<NaturalLanguageCoordinates> natLangCoordsWithLocalizationMessages = naturalLanguageService.findNaturalLanguagesWithLocalizationMessages();

        return new GetAllNaturalLanguagesResult()
                .naturalLanguages(
                        NaturalLanguage.getAll(context).stream()
                                .map(nl -> new org.haiku.haikudepotserver.api2.model.NaturalLanguage()
                                        .code(nl.getCode())
                                        .name(null == naturalLanguage
                                                ? nl.getName()
                                                : messageSource.getMessage(
                                                nl.getTitleKey(),
                                                null, // params
                                                naturalLanguage.toCoordinates().toLocale()))
                                        .isPopular(nl.getIsPopular())
                                        .hasData(natLangCoordsWithData.contains(nl.toCoordinates()))
                                        .hasLocalizationMessages(natLangCoordsWithLocalizationMessages.contains(nl.toCoordinates())))
                                .collect(Collectors.toList()));
    }

    public GetAllPkgCategoriesResult getAllPkgCategories(GetAllPkgCategoriesRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        final ObjectContext context = serverRuntime.newContext();

        final NaturalLanguage naturalLanguage =
                Optional.ofNullable(request.getNaturalLanguageCode())
                        .filter(StringUtils::isNotBlank)
                        .map(c -> getNaturalLanguage(context, c))
                        .orElse(null);

        return new GetAllPkgCategoriesResult()
                .pkgCategories(PkgCategory.getAll(context).stream()
                        .map(pc -> new org.haiku.haikudepotserver.api2.model.PkgCategory()
                                .code(pc.getCode())
                                .name(null == naturalLanguage
                                        ? pc.getName()
                                        : messageSource.getMessage(
                                        pc.getTitleKey(),
                                        null, // params
                                        naturalLanguage.toCoordinates().toLocale()))
                        )
                        .collect(Collectors.toList()));
    }

    public GetAllProminencesResult getAllProminences(GetAllProminencesRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        final ObjectContext context = serverRuntime.newContext();

        return new GetAllProminencesResult()
                .prominences(Prominence.getAll(context)
                        .stream()
                        .map(p -> new org.haiku.haikudepotserver.api2.model.Prominence()
                                .ordering(p.getOrdering())
                                .name(p.getName()))
                        .collect(Collectors.toList()));
    }


    public GetAllUserRatingStabilitiesResult getAllUserRatingStabilities(GetAllUserRatingStabilitiesRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        final ObjectContext context = serverRuntime.newContext();

        final NaturalLanguage naturalLanguage =
                Optional.ofNullable(request.getNaturalLanguageCode())
                        .filter(StringUtils::isNotBlank)
                        .map(c -> getNaturalLanguage(context, c))
                        .orElse(null);

        return new GetAllUserRatingStabilitiesResult()
                .userRatingStabilities(UserRatingStability.getAll(context)
                        .stream()
                        .map(urs -> new org.haiku.haikudepotserver.api2.model.UserRatingStability()
                                .code(urs.getCode())
                                .ordering(urs.getOrdering())
                                .name(null == naturalLanguage
                                        ? urs.getName()
                                        : messageSource.getMessage(
                                        urs.getTitleKey(),
                                        null, // params
                                        naturalLanguage.toCoordinates().toLocale())))
                        .collect(Collectors.toList()));
    }

    public GetRuntimeInformationResult getRuntimeInformation() {
        final ObjectContext context = serverRuntime.newContext();
        Optional<User> authUserOptional = tryObtainAuthenticatedUser(context);

        GetRuntimeInformationResult result = new GetRuntimeInformationResult()
                .projectVersion(runtimeInformationService.getProjectVersion())
                .isProduction(isProduction)
                .defaults(new GetRuntimeInformationResultDefaults()
                        .architectureCode(architectureDefaultCode)
                        .repositoryCode(repositoryDefaultCode));

        if(authUserOptional.isPresent() && authUserOptional.get().getIsRoot()) {
            result = result
                    .javaVersion(runtimeInformationService.getJavaVersion())
                    .startTimestamp(runtimeInformationService.getStartTimestamp());
        }

        return result;
    }


    public void  raiseException() {
        final ObjectContext context = serverRuntime.newContext();
        Optional<User> authUserOptional = tryObtainAuthenticatedUser(context).filter(_User::getIsRoot);

        if (authUserOptional.isPresent()) {
            throw new Error("test exception");
        }

        LOGGER.warn("attempt to raise a test exception without being authenticated as root");
    }

}
