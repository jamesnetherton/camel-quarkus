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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.quarkus.dev.spi.HotReplacementContext;
import io.quarkus.dev.spi.HotReplacementSetup;

public class CamelHotReplacementSetup implements HotReplacementSetup {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private HotReplacementContext context;
    private ScheduledFuture<?> scanTask;

    @Override
    public void setupHotDeployment(HotReplacementContext context) {
        this.context = context;
        CamelHotReplacementObserver.registerHotReplacementContext(this);
    }

    public void scheduleScan() {
        scanTask = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    context.doScan(false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        if (scanTask != null) {
            scanTask.cancel(false);
        }
    }
}
