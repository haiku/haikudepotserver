/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.cayenne;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.cayenne.CayenneRuntimeException;
import org.apache.cayenne.cache.QueryCacheEntryFactory;
import org.apache.cayenne.query.QueryMetadata;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>The Cayenne one seem to be broken so implementing an own one.</p>
 */

public class QueryCache implements org.apache.cayenne.cache.QueryCache {

    public final static int DEFAULT_CACHE_SIZE = 2000;

    private Cache<String, CacheEntry> cache;

    public QueryCache(int maxSize) {
        cache = CacheBuilder.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .build();
    }

    public QueryCache() {
        this(DEFAULT_CACHE_SIZE);
    }

    @Override
    public List get(QueryMetadata metadata) {
        String key = metadata.getCacheKey();
        if (StringUtils.isNotBlank(key)) {
            CacheEntry entry = cache.getIfPresent(key);

            if (null != entry) {
                return entry.getList();
            }
        }

        return null;
    }

    @Override
    public List get(QueryMetadata metadata, QueryCacheEntryFactory factory) {
        List result = get(metadata);
        if (result == null) {
            List newObject = factory.createObject();
            if (newObject == null) {
                throw new CayenneRuntimeException("Null on cache rebuilding: " + metadata.getCacheKey());
            }

            result = newObject;
            put(metadata, result);
        }

        return result;
    }

    @Override
    public void put(QueryMetadata metadata, List results) {
        String key = metadata.getCacheKey();
        if (StringUtils.isNotBlank(key)) {
            cache.put(key, new CacheEntry(results, metadata.getCacheGroup()));
        }
    }

    @Override
    public void remove(String key) {
        cache.invalidate(key);
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

    @Override
    public int size() {
        return (int) cache.size();
    }

    @Override
    public void removeGroup(String groupKey) {
        if (StringUtils.isNotBlank(groupKey)) {
            Collection<String> keysToRemove = cache
                    .asMap()
                    .entrySet()
                    .stream()
                    .filter(e -> StringUtils.equals(e.getValue().getCacheGroup(), groupKey))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            cache.invalidateAll(keysToRemove);
        }
    }

    final static class CacheEntry {

        private List<?> list;
        private String cacheGroup;

        public CacheEntry(List<?> list, String cacheGroup) {
            this.list = list;
            this.cacheGroup = cacheGroup;
        }

        public List<?> getList() {
            return list;
        }

        public String getCacheGroup() {
            return cacheGroup;
        }
    }


}
