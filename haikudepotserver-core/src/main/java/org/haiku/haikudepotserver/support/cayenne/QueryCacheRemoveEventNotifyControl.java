/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.cayenne;

import org.apache.commons.lang3.BooleanUtils;

/**
 * <p>This interface defines if the {@link NotifyingQueryCache} should notify
 * cache removal events. This is happening at the thread level. The purpose of
 * this is to prevent "notify echo" where one server notifies a removal which is
 * executed on another server which then notifies and so on.</p>
 */

public class QueryCacheRemoveEventNotifyControl {

    private final ThreadLocal<Boolean> isEnabled = ThreadLocal.withInitial(() -> true);

    public void enable() {
        isEnabled.set(true);
    }

    public void disable() {
        isEnabled.set(false);
    }

    public boolean isEnabled() {
        return BooleanUtils.isTrue(isEnabled.get());
    }

}
