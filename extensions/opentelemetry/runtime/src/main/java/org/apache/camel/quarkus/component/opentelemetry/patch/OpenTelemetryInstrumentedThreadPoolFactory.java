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
package org.apache.camel.quarkus.component.opentelemetry.patch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.context.Context;
import org.apache.camel.quarkus.component.opentelemetry.patch.internal.CurrentContextScheduledExecutorService;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.DefaultThreadPoolFactory;

/**
 * TODO: Remove this: https://github.com/apache/camel-quarkus/issues/6669
 */
@JdkService(ThreadPoolFactory.FACTORY)
public class OpenTelemetryInstrumentedThreadPoolFactory extends DefaultThreadPoolFactory implements ThreadPoolFactory {

    @Override
    public ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return Context.taskWrapping(super.newCachedThreadPool(threadFactory));
    }

    @Override
    public ExecutorService newThreadPool(
            int corePoolSize,
            int maxPoolSize,
            long keepAliveTime,
            TimeUnit timeUnit,
            int maxQueueSize,
            boolean allowCoreThreadTimeOut,
            RejectedExecutionHandler rejectedExecutionHandler,
            ThreadFactory threadFactory)
            throws IllegalArgumentException {

        ExecutorService executorService = super.newThreadPool(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                timeUnit,
                maxQueueSize,
                allowCoreThreadTimeOut,
                rejectedExecutionHandler,
                threadFactory);

        return Context.taskWrapping(executorService);
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
        return new CurrentContextScheduledExecutorService(super.newScheduledThreadPool(profile, threadFactory));
    }

}