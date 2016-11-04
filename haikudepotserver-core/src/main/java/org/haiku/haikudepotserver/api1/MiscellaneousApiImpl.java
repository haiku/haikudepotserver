/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api1.model.miscellaneous.*;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.feed.model.FeedService;
import org.haiku.haikudepotserver.feed.model.FeedSpecification;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@AutoJsonRpcServiceImpl(additionalPaths = "/api/v1/miscellaneous") // TODO; legacy path - remove
public class MiscellaneousApiImpl extends AbstractApiImpl implements MiscellaneousApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgApiImpl.class);

    private final static String RESOURCE_MESSAGES = "/messages%s.properties";
    private final static String RESOURCE_MESSAGES_NATURALLANGUAGE = "/naturallanguagemessages.properties";

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private RuntimeInformationService runtimeInformationService;

    @Resource
    private FeedService feedService;

    @Resource
    private MessageSource messageSource;

    @Resource
    private NaturalLanguageService naturalLanguageService;

    @Value("${deployment.isproduction:false}")
    private Boolean isProduction;

    @Override
    public GetAllPkgCategoriesResult getAllPkgCategories(GetAllPkgCategoriesRequest getAllPkgCategoriesRequest) {
        Preconditions.checkNotNull(getAllPkgCategoriesRequest);
        final ObjectContext context = serverRuntime.getContext();

        final Optional<NaturalLanguage> naturalLanguageOptional =
                Strings.isNullOrEmpty(getAllPkgCategoriesRequest.naturalLanguageCode)
                ? Optional.empty()
                        : NaturalLanguage.getByCode(context, getAllPkgCategoriesRequest.naturalLanguageCode);

        return new GetAllPkgCategoriesResult(
                PkgCategory.getAll(context).stream().map(pc -> {
                    if(naturalLanguageOptional.isPresent()) {
                        return new GetAllPkgCategoriesResult.PkgCategory(
                                pc.getCode(),
                                messageSource.getMessage(
                                        pc.getTitleKey(),
                                        null, // params
                                        naturalLanguageOptional.get().toLocale()));
                    }
                    else {
                        return new GetAllPkgCategoriesResult.PkgCategory(
                                pc.getCode(),
                                pc.getName());
                    }
                }).collect(Collectors.toList())
        );
    }

    @Override
    public GetAllNaturalLanguagesResult getAllNaturalLanguages(GetAllNaturalLanguagesRequest getAllNaturalLanguagesRequest) {
        Preconditions.checkNotNull(getAllNaturalLanguagesRequest);
        final ObjectContext context = serverRuntime.getContext();

        final Optional<NaturalLanguage> naturalLanguageOptional =
                Strings.isNullOrEmpty(getAllNaturalLanguagesRequest.naturalLanguageCode)
                        ? Optional.empty()
                        : NaturalLanguage.getByCode(context, getAllNaturalLanguagesRequest.naturalLanguageCode);

        return new GetAllNaturalLanguagesResult(
                NaturalLanguage.getAll(context).stream().map(nl -> {
                            if(naturalLanguageOptional.isPresent()) {
                                return new GetAllNaturalLanguagesResult.NaturalLanguage(
                                        nl.getCode(),
                                        messageSource.getMessage(
                                                nl.getTitleKey(),
                                                null, // params
                                                naturalLanguageOptional.get().toLocale()),
                                        nl.getIsPopular(),
                                        naturalLanguageService.hasData(nl.getCode()),
                                        naturalLanguageService.hasLocalizationMessages(nl.getCode()));
                            }
                            else {
                                return new GetAllNaturalLanguagesResult.NaturalLanguage(
                                        nl.getCode(),
                                        nl.getName(),
                                        nl.getIsPopular(),
                                        naturalLanguageService.hasData(nl.getCode()),
                                        naturalLanguageService.hasLocalizationMessages(nl.getCode()));
                            }
                        }
                ).collect(Collectors.toList())
        );

    }

    @Override
    public RaiseExceptionResult raiseException(RaiseExceptionRequest raiseExceptionRequest) {

        final ObjectContext context = serverRuntime.getContext();
        Optional<User> authUserOptional = tryObtainAuthenticatedUser(context);

        if(authUserOptional.isPresent() && authUserOptional.get().getIsRoot()) {
            throw new RuntimeException("test exception");
        }

        LOGGER.warn("attempt to raise a test exception without being authenticated as root");

        return new RaiseExceptionResult();
    }

    @Override
    public GetRuntimeInformationResult getRuntimeInformation(GetRuntimeInformationRequest getRuntimeInformationRequest) {

        final ObjectContext context = serverRuntime.getContext();
        Optional<User> authUserOptional = tryObtainAuthenticatedUser(context);

        GetRuntimeInformationResult result = new GetRuntimeInformationResult();
        result.projectVersion = runtimeInformationService.getProjectVersion();
        result.getBulkPkgRequestLimit = PkgApi.GETBULKPKG_LIMIT;
        result.isProduction = isProduction;

        if(authUserOptional.isPresent() && authUserOptional.get().getIsRoot()) {
            result.javaVersion = runtimeInformationService.getJavaVersion();
            result.startTimestamp = runtimeInformationService.getStartTimestamp();
        }

        return result;
    }

    @Override
    public GetAllArchitecturesResult getAllArchitectures(GetAllArchitecturesRequest getAllArchitecturesRequest) {
        Preconditions.checkNotNull(getAllArchitecturesRequest);
        GetAllArchitecturesResult result = new GetAllArchitecturesResult();
        result.architectures =
                Architecture.getAll(serverRuntime.getContext())
                        .stream()
                        .filter(a -> !a.getCode().equals(Architecture.CODE_SOURCE) && !a.getCode().equals(Architecture.CODE_ANY))
                        .map(a -> new GetAllArchitecturesResult.Architecture(a.getCode()))
                        .collect(Collectors.toList());

        return result;
    }

    @Override
    public GetAllMessagesResult getAllMessages(GetAllMessagesRequest getAllMessagesRequest) throws ObjectNotFoundException {
        Preconditions.checkNotNull(getAllMessagesRequest);
        Preconditions.checkNotNull(getAllMessagesRequest.naturalLanguageCode);

        ObjectContext context = serverRuntime.getContext();

        Optional<NaturalLanguage> naturalLanguageOptional = NaturalLanguage.getByCode(context, getAllMessagesRequest.naturalLanguageCode);

        if(!naturalLanguageOptional.isPresent()) {
            throw new ObjectNotFoundException(NaturalLanguage.class.getSimpleName(), getAllMessagesRequest.naturalLanguageCode);
        }

        Properties allLocalizationMessages = naturalLanguageService.getAllLocalizationMessages(
                naturalLanguageOptional.get().getCode());

        GetAllMessagesResult getAllMessagesResult = new GetAllMessagesResult();
        getAllMessagesResult.messages = new HashMap<>();

        for(Object key : allLocalizationMessages.keySet()) {
            getAllMessagesResult.messages.put(key.toString(), allLocalizationMessages.get(key).toString());
        }

        return getAllMessagesResult;
    }

    @Override
    public GetAllUserRatingStabilitiesResult getAllUserRatingStabilities(GetAllUserRatingStabilitiesRequest getAllUserRatingStabilitiesRequest) {
        Preconditions.checkNotNull(getAllUserRatingStabilitiesRequest);
        final ObjectContext context = serverRuntime.getContext();

        final Optional<NaturalLanguage> naturalLanguageOptional =
                Strings.isNullOrEmpty(getAllUserRatingStabilitiesRequest.naturalLanguageCode)
                        ? Optional.empty()
                        : NaturalLanguage.getByCode(context, getAllUserRatingStabilitiesRequest.naturalLanguageCode);

        return new GetAllUserRatingStabilitiesResult(
                UserRatingStability.getAll(context)
                        .stream()
                        .map(urs -> {
                            if(naturalLanguageOptional.isPresent()) {
                                return new GetAllUserRatingStabilitiesResult.UserRatingStability(
                                        urs.getCode(),
                                        messageSource.getMessage(
                                                urs.getTitleKey(),
                                                null, // params
                                                naturalLanguageOptional.get().toLocale()));
                            }

                            return new GetAllUserRatingStabilitiesResult.UserRatingStability(
                                    urs.getCode(),
                                    urs.getName());
                        })
                        .collect(Collectors.toList())
        );
    }

    @Override
    public GetAllProminencesResult getAllProminences(GetAllProminencesRequest request) {
        Preconditions.checkNotNull(request);
        final ObjectContext context = serverRuntime.getContext();

        return new GetAllProminencesResult(
                Prominence.getAll(context)
                .stream()
                .map(p -> new GetAllProminencesResult.Prominence(p.getOrdering(),p.getName()))
                .collect(Collectors.toList())
        );
    }

    @Override
    public GenerateFeedUrlResult generateFeedUrl(final GenerateFeedUrlRequest request) throws ObjectNotFoundException {
        Preconditions.checkNotNull(request);

        final ObjectContext context = serverRuntime.getContext();
        FeedSpecification specification = new FeedSpecification();
        specification.setLimit(request.limit);

        if(null!=request.supplierTypes) {
            specification.setSupplierTypes(
                    request.supplierTypes
                    .stream()
                    .map(st -> FeedSpecification.SupplierType.valueOf(st.name()))
                    .collect(Collectors.toList())
            );
        }

        if(null!=request.naturalLanguageCode) {
            specification.setNaturalLanguageCode(getNaturalLanguage(context, request.naturalLanguageCode).getCode());
        }

        if(null!=request.pkgNames) {
            List<String> checkedPkgNames = new ArrayList<>();

            for (String pkgName : request.pkgNames) {
                Optional<Pkg> pkgOptional = Pkg.getByName(context, pkgName);

                if (!pkgOptional.isPresent()) {
                    throw new ObjectNotFoundException(Pkg.class.getSimpleName(), pkgName);
                }

                checkedPkgNames.add(pkgOptional.get().getName());
            }

            specification.setPkgNames(checkedPkgNames);
        }

        GenerateFeedUrlResult result = new GenerateFeedUrlResult();
        result.url = feedService.generateUrl(specification);
        return result;
    }

}
