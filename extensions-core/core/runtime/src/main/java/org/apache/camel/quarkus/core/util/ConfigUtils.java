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
package org.apache.camel.quarkus.core.util;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

public final class ConfigUtils {
    private static final Logger LOG = Logger.getLogger(ConfigUtils.class);

    private ConfigUtils() {
        // Utility class
    }

    public static Properties getPropertiesForActiveProfiles() {
        final Properties answer = new Properties();
        final Config config = ConfigProvider.getConfig();
        final List<String> profiles = io.quarkus.runtime.configuration.ConfigUtils.getProfiles();
        for (String name : config.getPropertyNames()) {
            try {
                if (isValidForActiveProfiles(name, profiles)) {
                    answer.put(name, config.getValue(name, String.class));
                }
            } catch (NoSuchElementException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debugf("Failed to resolve property %s due to %s", name, e.getMessage());
                }
            }
        }
        return answer;
    }

    private static boolean isValidForActiveProfiles(String name, List<String> profiles) {
        if (!profiles.isEmpty() && name.startsWith("%") && name.contains(".")) {
            String prefix = name.substring(1, name.indexOf('.'));
            List<String> propertyProfiles = Arrays.asList(prefix.split(","));
            return propertyProfiles.stream().anyMatch(profiles::contains);
        }
        return true;
    }
}
