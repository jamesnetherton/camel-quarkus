/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.core.devmode;

import javax.enterprise.event.Observes;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;

/**
 * Observer class to handle the life cycle of {@link CamelHotReplacementSetup}. It is configured at build time
 * only in dev mode and if Camel live reloading is enabled.
 *
 * {@link StartupEvent} initiates scanning for code changes and trigger live reloading
 *
 * {@link ShutdownEvent} stops scanning and performs clean up tasks
 */
public final class CamelHotReplacementObserver {

    private static final Logger LOG = Logger.getLogger(CamelHotReplacementObserver.class);
    private static CamelHotReplacementSetup hotReplacementSetup;

    public static void registerHotReplacementContext(CamelHotReplacementSetup hotReplacementSetup) {
        if (hotReplacementSetup == null) {
            throw new IllegalArgumentException("CamelHotReplacementSetup instance cannot be null");
        }
        CamelHotReplacementObserver.hotReplacementSetup = hotReplacementSetup;
    }

    void onStartup(@Observes StartupEvent event) {
        if (hotReplacementSetup == null) {
            LOG.warn("CamelHotReplacementSetup instance is null. Live reloading of Camel routes is disabled.");
            return;
        }
        hotReplacementSetup.scheduleScan();
    }

    void onShutdown(@Observes ShutdownEvent event) {
        if (hotReplacementSetup != null) {
            hotReplacementSetup.close();
        }
    }
}
