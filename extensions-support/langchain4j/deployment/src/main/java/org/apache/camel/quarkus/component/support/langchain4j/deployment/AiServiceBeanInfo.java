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
package org.apache.camel.quarkus.component.support.langchain4j.deployment;

/**
 * Build-time metadata about a discovered AiService bean.
 * <p>
 * This information is collected during the build process and used to create
 * synthetic Agent adapter beans at runtime.
 */
public class AiServiceBeanInfo {

    private final String interfaceName;
    private final String beanName;
    private final String chatMethodName;
    private final String parameterTypeName;
    private String generatedAdapterClassName;

    public AiServiceBeanInfo(String interfaceName, String beanName, String chatMethodName, String parameterTypeName) {
        this.interfaceName = interfaceName;
        this.beanName = beanName;
        this.chatMethodName = chatMethodName;
        this.parameterTypeName = parameterTypeName;
    }

    /**
     * @return the fully qualified name of the AiService interface
     */
    public String getInterfaceName() {
        return interfaceName;
    }

    /**
     * @return the CDI bean name (used for bean lookup)
     */
    public String getBeanName() {
        return beanName;
    }

    /**
     * @return the name of the method to invoke on the AiService
     */
    public String getChatMethodName() {
        return chatMethodName;
    }

    /**
     * @return the fully qualified name of the parameter type
     */
    public String getParameterTypeName() {
        return parameterTypeName;
    }

    /**
     * @return the adapter bean name (original bean name + "$Agent" suffix)
     */
    public String getAdapterBeanName() {
        return beanName + "$Agent";
    }

    /**
     * @return the fully qualified name of the generated adapter class
     */
    public String getGeneratedAdapterClassName() {
        return generatedAdapterClassName;
    }

    /**
     * Set the generated adapter class name (populated during bytecode generation).
     */
    public void setGeneratedAdapterClassName(String generatedAdapterClassName) {
        this.generatedAdapterClassName = generatedAdapterClassName;
    }

    @Override
    public String toString() {
        return "AiServiceBeanInfo{" +
                "interface=" + interfaceName +
                ", beanName=" + beanName +
                ", adapterName=" + getAdapterBeanName() +
                ", method=" + chatMethodName +
                ", paramType=" + parameterTypeName +
                ", generatedClass=" + generatedAdapterClassName +
                '}';
    }
}
