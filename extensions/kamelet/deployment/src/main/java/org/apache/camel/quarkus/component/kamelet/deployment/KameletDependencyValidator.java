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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkus.maven.dependency.ResolvedDependency;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.yaml.KameletRoutesBuilderLoader;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.nodeAt;

public class KameletDependencyValidator extends KameletRoutesBuilderLoader implements DependencyValidator {
    private final Set<KameletDependency> dependencies = new HashSet<>();

    @Override
    protected RouteBuilder builder(YamlDeserializationContext ctx, Node node) {
        Node dependencyNodes = nodeAt(node, "/spec/dependencies");
        if (dependencyNodes != null && dependencyNodes.getNodeType() == NodeType.SEQUENCE) {
            SequenceNode sequenceNode = (SequenceNode) dependencyNodes;
            for (Node child : sequenceNode.getValue()) {
                if (child.getNodeType() == NodeType.SCALAR) {
                    ScalarNode scalarNode = (ScalarNode) child;
                    KameletDependency dependency = KameletDependency.of(scalarNode.getValue());
                    if (dependency != null) {
                        dependencies.add(dependency);
                    }
                }
            }
        }

        return super.builder(ctx, node);
    }

    @Override
    public void validateDependencies(Collection<ResolvedDependency> runtimeDependencies) {
        Set<KameletDependency> missingDependencies = dependencies.stream()
                .filter(KameletDependency::isValid)
                .filter(dependency -> isRuntimeDependencyMissing(dependency, runtimeDependencies))
                .collect(Collectors.toUnmodifiableSet());

        if (!missingDependencies.isEmpty()) {
            StringBuilder message = new StringBuilder(
                    "Required Kamelet dependencies are not present.\n\nAdd the following dependencies to your application:\n\n");
            missingDependencies.stream()
                    .sorted()
                    .forEach(dep -> message.append(dep.toString()).append("\n"));
            throw new IllegalStateException(message.toString());
        }
    }

    private boolean isRuntimeDependencyMissing(
            KameletDependency dependency,
            Collection<ResolvedDependency> runtimeDependencies) {

        // NOTE: Dependency version is excluded from matching since it's possible that a dependency could be managed by imported BOMs
        return runtimeDependencies.stream()
                .noneMatch(dep -> dependency.getGroupId().equals(dep.getGroupId())
                        && dependency.getArtifactId().equals(dep.getArtifactId()));
    }
}
