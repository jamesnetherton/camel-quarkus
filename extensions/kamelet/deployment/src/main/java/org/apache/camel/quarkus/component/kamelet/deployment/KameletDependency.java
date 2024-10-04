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
package org.apache.camel.quarkus.component.kamelet.deployment;

import java.util.Objects;

import org.apache.camel.util.ObjectHelper;

final class KameletDependency implements Comparable<KameletDependency> {
    final String groupId;
    final String artifactId;
    final String version;

    KameletDependency(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public boolean isValid() {
        return groupId != null && artifactId != null;
    }

    @Override
    public String toString() {
        if (isValid()) {
            if (version != null) {
                return "%s:%s:%s".formatted(groupId, artifactId, version);
            } else {
                return "%s:%s".formatted(groupId, artifactId);
            }
        }
        return "Unresolvable GAV";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        KameletDependency kameletDependency = (KameletDependency) o;
        return Objects.equals(groupId, kameletDependency.groupId) && Objects.equals(artifactId, kameletDependency.artifactId)
                && Objects.equals(version, kameletDependency.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }

    static KameletDependency of(String gavString) {
        if (ObjectHelper.isNotEmpty(gavString)) {
            String[] parts = gavString.split(":");
            if (parts[0].equals("camel") && parts.length == 2) {
                return new KameletDependency("org.apache.camel.quarkus", "camel-quarkus-" + parts[1], null);
            }

            if (parts[0].equals("mvn") && parts.length == 4) {
                return new KameletDependency(parts[1], parts[2], parts[3]);
            }
        }
        return null;
    }

    @Override
    public int compareTo(KameletDependency other) {
        int groupIdComparison = getGroupId().compareTo(other.getGroupId());
        if (groupIdComparison != 0) {
            return groupIdComparison;
        }
        return getGroupId().compareTo(other.getArtifactId());
    }
}
