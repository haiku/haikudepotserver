/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.logging;

import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * <p>This class takes responsibility for configuring the various logging systems to work together.</p>
 */

public class LoggingSetupOrchestration {

    public void init() {

        // re-pipe java util logging into SLF4J
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

}
