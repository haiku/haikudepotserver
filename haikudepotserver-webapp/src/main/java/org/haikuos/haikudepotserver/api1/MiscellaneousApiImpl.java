/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.api1.model.miscellaneous.*;
import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.support.Closeables;
import org.haikuos.haikudepotserver.support.RuntimeInformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

@Component
public class MiscellaneousApiImpl extends AbstractApiImpl implements MiscellaneousApi {

    protected static Logger logger = LoggerFactory.getLogger(PkgApiImpl.class);

    public final static String RESOURCE_MESSAGES = "/messages.properties";

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    RuntimeInformationService runtimeInformationService;

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
    public GetAllMessagesResult getAllMessages(GetAllMessagesRequest getAllMessagesRequest) {
        Preconditions.checkNotNull(getAllMessagesRequest);

        InputStream inputStream = null;

        try {
            inputStream = getClass().getResourceAsStream(RESOURCE_MESSAGES);

            if(null==inputStream) {
                throw new FileNotFoundException(RESOURCE_MESSAGES);
            }

            Properties properties = new Properties();
            properties.load(inputStream);
            Map<String,String> map = Maps.newHashMap();

            for(String propertyName : properties.stringPropertyNames()) {
                map.put(propertyName, properties.get(propertyName).toString());
            }

            GetAllMessagesResult getAllMessagesResult = new GetAllMessagesResult();
            getAllMessagesResult.messages = map;
            return getAllMessagesResult;
        }
        catch(IOException ioe) {
            throw new RuntimeException("unable to assemble the messages to send for api1",ioe);
        }
        finally {
            Closeables.closeQuietly(inputStream);
        }
    }
}
