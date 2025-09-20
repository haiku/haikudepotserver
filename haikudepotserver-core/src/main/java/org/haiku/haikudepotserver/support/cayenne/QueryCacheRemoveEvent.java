/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.cayenne;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.commons.collections4.CollectionUtils;
import org.haiku.haikudepotserver.support.eventing.model.InterProcessEvent;

import java.util.Collection;
import java.util.List;

/**
 * <p>This event is triggering a Cayenne cache drop. It is sent when a cache is evicted
 * and will cause other instances of HDS to likewise drop their caches too.</p>
 */

public class QueryCacheRemoveEvent extends InterProcessEvent {

    private final List<Remove> removes;

    @JsonCreator
    public QueryCacheRemoveEvent(
            @JsonProperty("removes") Collection<Remove> removes
    ) {
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(removes));
        this.removes = List.copyOf(removes);
    }

    public List<Remove> getRemoves() {
        return removes;
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = KeyRemove.class, name = "KeyRemove"),
            @JsonSubTypes.Type(value = GroupRemove.class, name = "GroupRemove"),
            @JsonSubTypes.Type(value = GroupWithTypesRemove.class, name = "GroupWithTypesRemove"),
            @JsonSubTypes.Type(value = ClearRemove.class, name = "ClearRemove")
    })
    public sealed static abstract class Remove
            permits ClearRemove, KeyRemove, GroupRemove, GroupWithTypesRemove {
    }

    public static final class ClearRemove extends Remove {

        @JsonCreator
        public ClearRemove() {
        }
    }

    public static final class KeyRemove extends Remove {

        private final String key;

        @JsonCreator
        public KeyRemove(
                @JsonProperty("key") String key
        ) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(key));
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    public static final class GroupRemove extends Remove {
        private final String groupKey;

        @JsonCreator
        public GroupRemove(
                @JsonProperty("groupKey") String groupKey
        ) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(groupKey));
            this.groupKey = groupKey;
        }

        public String getGroupKey() {
            return groupKey;
        }

    }

    public static final class GroupWithTypesRemove extends Remove {

        private final String groupKey;
        private final String keyTypeClassName;
        private final String valueTypeClassName;

        @JsonCreator
        public GroupWithTypesRemove(
                @JsonProperty("groupKey") String groupKey,
                @JsonProperty("keyTypeClassName") String keyTypeClassName,
                @JsonProperty("valueTypeClassName") String valueTypeClassName
        ) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(groupKey));
            Preconditions.checkArgument(!Strings.isNullOrEmpty(keyTypeClassName));
            Preconditions.checkArgument(!Strings.isNullOrEmpty(valueTypeClassName));
            this.groupKey = groupKey;
            this.keyTypeClassName = keyTypeClassName;
            this.valueTypeClassName = valueTypeClassName;
        }

        public String getGroupKey() {
            return groupKey;
        }

        public String getKeyTypeClassName() {
            return keyTypeClassName;
        }

        public String getValueTypeClassName() {
            return valueTypeClassName;
        }
    }

}