/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import com.google.common.base.Preconditions;

import java.util.Optional;
import java.util.stream.Collector;

/**
 * <p>This collector will return an optional that contains the only object from the stream or it will
 * contain an absent optional in the case that the stream was empty.  If the stream contained more
 * than one object then it will throw a runtime exception.</p>
 */

public class SingleCollector {

    public static <T> Collector<T, ResultHolder<T>, T> single() {
        //noinspection Convert2MethodRef
        return Collector.of(
                ResultHolder::new,
                (h,i) -> {
                    if(!h.isPresent()) {
                        h.setValue(i);
                    }
                    else {
                        throw new IllegalStateException("expecting none or one element");
                    }
                },
                (h1, h2) -> {
                    if(h1.isPresent() && h2.isPresent()) {
                        throw new IllegalStateException("expecting none or one element");
                    }

                    return h1.isPresent() ? h1 : h2;
                },
                h -> {
                    if(!h.isPresent()) {
                        throw new IllegalStateException("expecting one element");
                    }

                    return h.getValue();
                }
        );
    }

    public static <T> Collector<T, ResultHolder<T>, Optional<T>> optional() {
        //noinspection Convert2MethodRef
        return Collector.of(
                ResultHolder::new,
                (h,i) -> {
                    if(!h.isPresent()) {
                        h.setValue(i);
                    }
                    else {
                        throw new IllegalStateException("expecting none or one element");
                    }
                },
                (h1, h2) -> {
                    if(h1.isPresent() && h2.isPresent()) {
                        throw new IllegalStateException("expecting none or one element");
                    }

                    return h1.isPresent() ? h1 : h2;
                },
                h -> h.toOptional()
        );
    }

    /**
     * <p>This acts like a mutable {@link Optional}.</p>
     */

    public static class ResultHolder<T> {

        private T value;

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            Preconditions.checkArgument(null!=value, "the value cannot be set to null");
            this.value = value;
        }

        public boolean isPresent() {
            return null!=value;
        }

        public Optional toOptional() {
            return Optional.ofNullable(getValue());
        }

    }

}