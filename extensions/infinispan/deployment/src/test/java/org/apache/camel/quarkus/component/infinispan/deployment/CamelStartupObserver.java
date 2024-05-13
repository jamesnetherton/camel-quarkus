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
package org.apache.camel.quarkus.component.infinispan.deployment;

import jakarta.enterprise.event.Observes;
import org.apache.camel.impl.event.CamelContextStartedEvent;
import org.apache.camel.support.CamelContextHelper;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.jboss.logging.Logger;

public class CamelStartupObserver {
    public static final String LOG_MESSAGE = "Default Infinispan cache bean created: ";
    private static final Logger LOG = Logger.getLogger(CamelStartupObserver.class.getName());

    void init(@Observes CamelContextStartedEvent event) {
        RemoteCacheManager cache = CamelContextHelper.findSingleByType(event.getContext(), RemoteCacheManager.class);
        LOG.info(LOG_MESSAGE + (cache != null));
    }
}
