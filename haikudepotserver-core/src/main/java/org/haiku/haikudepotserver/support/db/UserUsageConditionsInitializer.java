/*
 * Copyright 2019-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.db;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.haiku.haikudepotserver.dataobjects.HaikuDepot;
import org.haiku.haikudepotserver.dataobjects.UserUsageConditions;
import org.haiku.haikudepotserver.dataobjects.auto._UserUsageConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>This class is dedicated to loading up the user usage conditions.  These
 * are stored on disk and are then populated into the database like migrations
 * at startup time.</p>
 */

public class UserUsageConditionsInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserUsageConditionsInitializer.class);

    private static final String RESOURCE_PREFIX = "classpath:/userusageconditions/";

    private final ServerRuntime serverRuntime;
    private final ObjectMapper objectMapper;

    public UserUsageConditionsInitializer(
            ServerRuntime serverRuntime,
            ObjectMapper objectMapper) {
        this.serverRuntime = serverRuntime;
        this.objectMapper = objectMapper;
    }

    public void init() {
        initUserUsageConditions();
    }

    /**
     * <p>User usage conditions need to be loaded into the database.  These are
     * present as file-resources in the deployment.  Here those files are loaded
     * up and if they are not present in the database then the entries are
     * populated.</p>
     */

    private void initUserUsageConditions() {
        ObjectContext context = serverRuntime.newContext();
        List<UserUsageConditions> existingUserUsageConditions = UserUsageConditions.getAll(context);
        Set<String> existingUserUsageConditionCodes = existingUserUsageConditions
                .stream()
                .map(_UserUsageConditions::getCode)
                .collect(Collectors.toSet());
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        int initialOrdering = existingUserUsageConditions
                .stream()
                .mapToInt(_UserUsageConditions::getOrdering)
                .max()
                .orElse(100);
        MutableInt mutableOrdering = new MutableInt(initialOrdering);

        try {
            Arrays.stream(resolver.getResources(RESOURCE_PREFIX + "*.json"))
                    .filter(r -> StringUtils.isNotEmpty(r.getFilename()))
                    .filter(r -> !existingUserUsageConditionCodes.contains(Files.getNameWithoutExtension(r.getFilename())))
                    .sorted(Comparator.comparing(Resource::getFilename))
                    .map(r -> Pair.of(
                            r,
                            resolver.getResource(RESOURCE_PREFIX + Files.getNameWithoutExtension(r.getFilename()) + ".md")
                    ))
                    .forEach(p -> initUserUsageConditions(context, p, mutableOrdering.incrementAndGet()));
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }

        if (mutableOrdering.getValue() > initialOrdering) {
            LOGGER.info("did create {} user usage conditions", mutableOrdering.getValue() - initialOrdering);
            context.commitChanges();
        }

        serverRuntime.getDataDomain().getQueryCache()
                .removeGroup(HaikuDepot.CacheGroup.USER_USAGE_CONDITIONS.name());
    }

    private void initUserUsageConditions(ObjectContext context, Pair<Resource, Resource> resources, int ordering) {
        Resource metaDataResource = resources.getLeft();
        Resource markdownResource = resources.getRight();

        if (null == metaDataResource || !metaDataResource.exists()) {
            throw new IllegalStateException("unable to find the meta data resource");
        }

        if (null == markdownResource || !markdownResource.exists()) {
            throw new IllegalStateException("unable to find the markdown resource");
        }

        try (
                InputStream metaDataInputStream = metaDataResource.getInputStream();
                InputStream markdownInputStream = markdownResource.getInputStream()) {
            UserUsageConditionsMetaData metaData = objectMapper.readValue(metaDataInputStream, UserUsageConditionsMetaData.class);
            String markdownString = StreamUtils.copyToString(markdownInputStream, StandardCharsets.UTF_8);
            UserUsageConditions userUsageConditions = context.newObject(UserUsageConditions.class);
            userUsageConditions.setCode(Files.getNameWithoutExtension(metaDataResource.getFilename()));
            userUsageConditions.setCopyMarkdown(markdownString);
            userUsageConditions.setMinimumAge(metaData.getMinimumAge());
            userUsageConditions.setOrdering(ordering);

            LOGGER.info("will create user usage conditions [{}]", userUsageConditions.getCode());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * <p>Each of the user usage conditions stored on disk has some meta-data
     * stored with it.  This class models that meta-data.</p>
     */

    public static class UserUsageConditionsMetaData {

        private final Integer minimumAge;

        @JsonCreator
        public UserUsageConditionsMetaData(
                @JsonProperty("minimumAge") Integer minimumAge) {
            Preconditions.checkNotNull(minimumAge);
            this.minimumAge = minimumAge;
        }


        public Integer getMinimumAge() {
            return minimumAge;
        }
    }

}
