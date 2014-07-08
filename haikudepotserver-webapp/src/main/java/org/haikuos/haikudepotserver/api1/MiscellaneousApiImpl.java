/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.*;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.api1.model.miscellaneous.*;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.security.AuthorizationService;
import org.haikuos.haikudepotserver.security.model.Permission;
import org.haikuos.haikudepotserver.security.model.TargetType;
import org.haikuos.haikudepotserver.support.Closeables;
import org.haikuos.haikudepotserver.support.RuntimeInformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Properties;

@Component
public class MiscellaneousApiImpl extends AbstractApiImpl implements MiscellaneousApi {

    protected static Logger logger = LoggerFactory.getLogger(PkgApiImpl.class);

    public final static String RESOURCE_MESSAGES = "/messages%s.properties";

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    AuthorizationService authorizationService;

    @Resource
    RuntimeInformationService runtimeInformationService;

    @Override
    public GetAllPkgCategoriesResult getAllPkgCategories(GetAllPkgCategoriesRequest getAllPkgCategoriesRequest) {
        Preconditions.checkNotNull(getAllPkgCategoriesRequest);
        final ObjectContext context = serverRuntime.getContext();

        return new GetAllPkgCategoriesResult(
                Lists.transform(
                        PkgCategory.getAll(context),
                        new Function<PkgCategory, GetAllPkgCategoriesResult.PkgCategory>() {
                            @Override
                            public GetAllPkgCategoriesResult.PkgCategory apply(PkgCategory input) {
                                return new GetAllPkgCategoriesResult.PkgCategory(
                                        input.getCode(),
                                        input.getName());
                            }
                        }
                )
        );
    }

    @Override
    public GetAllNaturalLanguagesResult getAllNaturalLanguages(GetAllNaturalLanguagesRequest getAllNaturalLanguagesRequest) {
        Preconditions.checkNotNull(getAllNaturalLanguagesRequest);
        final ObjectContext context = serverRuntime.getContext();

        return new GetAllNaturalLanguagesResult(
                Lists.transform(
                        NaturalLanguage.getAll(context),
                        new Function<NaturalLanguage, GetAllNaturalLanguagesResult.NaturalLanguage>() {
                            @Override
                            public GetAllNaturalLanguagesResult.NaturalLanguage apply(NaturalLanguage input) {
                                return new GetAllNaturalLanguagesResult.NaturalLanguage(
                                        input.getCode(),
                                        input.getName());
                            }
                        }
                )
        );
    }

    @Override
    public CheckAuthorizationResult checkAuthorization(CheckAuthorizationRequest deriveAuthorizationRequest) {

        Preconditions.checkNotNull(deriveAuthorizationRequest);
        Preconditions.checkNotNull(deriveAuthorizationRequest.targetAndPermissions);

        final ObjectContext context = serverRuntime.getContext();
        CheckAuthorizationResult result = new CheckAuthorizationResult();
        result.targetAndPermissions = Lists.newArrayList();

        for(CheckAuthorizationRequest.AuthorizationTargetAndPermission targetAndPermission : deriveAuthorizationRequest.targetAndPermissions) {

            CheckAuthorizationResult.AuthorizationTargetAndPermission authorizationTargetAndPermission = new CheckAuthorizationResult.AuthorizationTargetAndPermission();

            authorizationTargetAndPermission.permissionCode = targetAndPermission.permissionCode;
            authorizationTargetAndPermission.targetIdentifier = targetAndPermission.targetIdentifier;
            authorizationTargetAndPermission.targetType = targetAndPermission.targetType;

            authorizationTargetAndPermission.authorized = authorizationService.check(
                    context,
                    tryObtainAuthenticatedUser(context).orNull(),
                    null!=targetAndPermission.targetType ? TargetType.valueOf(targetAndPermission.targetType.name()) : null,
                    targetAndPermission.targetIdentifier,
                    Permission.valueOf(targetAndPermission.permissionCode));

            result.targetAndPermissions.add(authorizationTargetAndPermission);
        }

        return result;
    }

    @Override
    public RaiseExceptionResult raiseException(RaiseExceptionRequest raiseExceptionRequest) {

        final ObjectContext context = serverRuntime.getContext();
        Optional<User> authUserOptional = tryObtainAuthenticatedUser(context);

        if(authUserOptional.isPresent() && authUserOptional.get().getIsRoot()) {
            throw new RuntimeException("test exception");
        }

        logger.warn("attempt to raise a test exception without being authenticated as root");

        return new RaiseExceptionResult();
    }

    @Override
    public GetRuntimeInformationResult getRuntimeInformation(GetRuntimeInformationRequest getRuntimeInformationRequest) {

        final ObjectContext context = serverRuntime.getContext();
        Optional<User> authUserOptional = tryObtainAuthenticatedUser(context);

        GetRuntimeInformationResult result = new GetRuntimeInformationResult();
        result.projectVersion = runtimeInformationService.getProjectVersion();
        result.getBulkPkgRequestLimit = PkgApi.GETBULKPKG_LIMIT;

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
                Lists.newArrayList(
                        Iterables.transform(

                                // we want to explicitly exclude 'source' and 'any' because they are pseudo
                                // architectures.

                                Iterables.filter(
                                        Architecture.getAll(serverRuntime.getContext()),
                                        new Predicate<Architecture>() {
                                            @Override
                                            public boolean apply(org.haikuos.haikudepotserver.dataobjects.Architecture input) {
                                                return
                                                        !input.getCode().equals(Architecture.CODE_SOURCE)
                                                                && !input.getCode().equals(Architecture.CODE_ANY);
                                            }
                                        }
                                ),
                                new Function<Architecture, GetAllArchitecturesResult.Architecture>() {
                                    @Override
                                    public GetAllArchitecturesResult.Architecture apply(org.haikuos.haikudepotserver.dataobjects.Architecture input) {
                                        GetAllArchitecturesResult.Architecture result = new GetAllArchitecturesResult.Architecture();
                                        result.code = input.getCode();
                                        return result;
                                    }
                                }
                        )
                );

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

        boolean isEnglish = naturalLanguageOptional.get().getCode().equals(NaturalLanguage.CODE_ENGLISH);

        String resourcePath = String.format(
                RESOURCE_MESSAGES,
                !isEnglish ? "_" + naturalLanguageOptional.get().getCode() : "");

        InputStream inputStream = null;
        InputStreamReader reader = null;

        try {
            inputStream = getClass().getResourceAsStream(resourcePath);

            if(null==inputStream) {
                throw new FileNotFoundException(resourcePath);
            }

            reader = new InputStreamReader(inputStream, Charsets.UTF_8);

            Properties properties = new Properties();
            properties.load(reader);
            Map<String,String> map = Maps.newHashMap();

            for(String propertyName : properties.stringPropertyNames()) {
                map.put(propertyName, properties.get(propertyName).toString());
            }

            GetAllMessagesResult getAllMessagesResult = new GetAllMessagesResult();
            getAllMessagesResult.messages = map;
            return getAllMessagesResult;
        }
        catch(IOException ioe) {
            throw new RuntimeException("unable to assemble the messages to send for api1 from; "+resourcePath,ioe);
        }
        finally {
            Closeables.closeQuietly(reader);
            Closeables.closeQuietly(inputStream);
        }
    }

    @Override
    public GetAllUserRatingStabilitiesResult getAllUserRatingStabilities(GetAllUserRatingStabilitiesRequest getAllUserRatingStabilitiesRequest) {
        Preconditions.checkNotNull(getAllUserRatingStabilitiesRequest);
        final ObjectContext context = serverRuntime.getContext();

        return new GetAllUserRatingStabilitiesResult(
                Lists.transform(
                        UserRatingStability.getAll(context),
                        new Function<UserRatingStability, GetAllUserRatingStabilitiesResult.UserRatingStability>() {
                            @Override
                            public GetAllUserRatingStabilitiesResult.UserRatingStability apply(UserRatingStability input) {
                                return new GetAllUserRatingStabilitiesResult.UserRatingStability(
                                        input.getCode(),
                                        input.getName());
                            }
                        }
                )
        );
    }

}
