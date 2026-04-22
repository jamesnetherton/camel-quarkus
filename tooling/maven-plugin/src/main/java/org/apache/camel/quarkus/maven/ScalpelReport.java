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
package org.apache.camel.quarkus.maven;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Model for Scalpel Maven extension JSON report.
 *
 * @see   <a href="https://github.com/maveniverse/scalpel">Scalpel</a>
 * @since 3.32.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScalpelReport {

    private String version;
    private String scalpelVersion;
    private String baseBranch;
    private boolean fullBuildTriggered;
    private String triggerFile;
    private List<String> changedFiles;
    private List<String> changedProperties;
    private List<String> changedManagedDependencies;
    private List<String> changedManagedPlugins;
    private List<ScalpelModule> affectedModules;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getScalpelVersion() {
        return scalpelVersion;
    }

    public void setScalpelVersion(String scalpelVersion) {
        this.scalpelVersion = scalpelVersion;
    }

    public String getBaseBranch() {
        return baseBranch;
    }

    public void setBaseBranch(String baseBranch) {
        this.baseBranch = baseBranch;
    }

    public boolean isFullBuildTriggered() {
        return fullBuildTriggered;
    }

    public void setFullBuildTriggered(boolean fullBuildTriggered) {
        this.fullBuildTriggered = fullBuildTriggered;
    }

    public String getTriggerFile() {
        return triggerFile;
    }

    public void setTriggerFile(String triggerFile) {
        this.triggerFile = triggerFile;
    }

    public List<String> getChangedFiles() {
        return changedFiles;
    }

    public void setChangedFiles(List<String> changedFiles) {
        this.changedFiles = changedFiles;
    }

    public List<String> getChangedProperties() {
        return changedProperties;
    }

    public void setChangedProperties(List<String> changedProperties) {
        this.changedProperties = changedProperties;
    }

    public List<String> getChangedManagedDependencies() {
        return changedManagedDependencies;
    }

    public void setChangedManagedDependencies(List<String> changedManagedDependencies) {
        this.changedManagedDependencies = changedManagedDependencies;
    }

    public List<String> getChangedManagedPlugins() {
        return changedManagedPlugins;
    }

    public void setChangedManagedPlugins(List<String> changedManagedPlugins) {
        this.changedManagedPlugins = changedManagedPlugins;
    }

    public List<ScalpelModule> getAffectedModules() {
        return affectedModules;
    }

    public void setAffectedModules(List<ScalpelModule> affectedModules) {
        this.affectedModules = affectedModules;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScalpelModule {
        private String groupId;
        private String artifactId;
        private String path;
        private String type;
        private List<String> reasons;

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<String> getReasons() {
            return reasons;
        }

        public void setReasons(List<String> reasons) {
            this.reasons = reasons;
        }
    }
}
