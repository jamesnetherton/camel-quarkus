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
package org.apache.camel.quarkus.core.deployment.spi;

import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A {@link MultiBuildItem} holding a class name referenced by a Camel route DSL construct.
 * <p>
 * Produced in native builds by the Java, XML and YAML DSL route scanners. Extensions consume the items of the
 * {@link Kind} they are interested in, for example to register the referenced classes for reflection.
 */
public final class CamelDslTypeReferenceBuildItem extends MultiBuildItem {

    /**
     * The kind of DSL construct that references the class.
     */
    public enum Kind {
        /**
         * An exception class referenced by {@code onException}, {@code doCatch} or {@code throwException}. Java DSL
         * scanning may also report class constants that are not exceptions, so consumers should verify the type.
         */
        EXCEPTION,
        /**
         * A bean class referenced by the bean EIP, such as {@code bean(MyBean.class)} or
         * {@code <bean beanType="..."/>}.
         */
        BEAN,
    }

    private final Kind kind;
    private final String className;

    public CamelDslTypeReferenceBuildItem(Kind kind, String className) {
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.className = Objects.requireNonNull(className, "className must not be null");
    }

    /**
     * @return the kind of DSL construct that references the class
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * @return the fully qualified name of the referenced class
     */
    public String getClassName() {
        return className;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CamelDslTypeReferenceBuildItem other)) {
            return false;
        }
        return kind == other.kind && className.equals(other.className);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, className);
    }

    @Override
    public String toString() {
        return kind + ":" + className;
    }
}
