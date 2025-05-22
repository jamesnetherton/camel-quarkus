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
package org.apache.camel.quarkus.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultDumpRoutesStrategy;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.ResourceHelper;

final class CamelQuarkusDumpTestRoutesStrategy extends DefaultDumpRoutesStrategy {
    private final List<Resource> resources = new ArrayList<>();

    CamelQuarkusDumpTestRoutesStrategy(CamelContext context) {
        setCamelContext(context);
        setInclude("*");
        setLog(false);
    }

    List<Resource> getResources() {
        return resources;
    }

    @Override
    protected void doDumpToDirectory(Resource resource, StringBuilder sbLocal, String kind, String ext, Set<String> files) {
        String resourceName = "%s-%d.%s".formatted(kind, resources.size() + 1, ext);
        System.out.println(sbLocal.toString());
        Resource stringResource = ResourceHelper.fromString(resourceName, sbLocal.toString());
        resources.add(stringResource);
    }
}
