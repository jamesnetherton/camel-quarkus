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
package org.apache.camel.quarkus.core.deployment.main;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.MainListener;

public final class CustomMainListener implements MainListener {

    @Override
    public void beforeInitialize(BaseMainSupport main) {
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:configure")
                        .id("configure")
                        .to("log:configure");
            }
        });
    }

    @Override
    public void beforeConfigure(BaseMainSupport main) {
    }

    @Override
    public void afterConfigure(BaseMainSupport main) {
    }

    @Override
    public void beforeStart(BaseMainSupport main) {
        main.configure().addRoutesBuilder(new MyBuilder());
    }

    @Override
    public void afterStart(BaseMainSupport main) {
    }

    @Override
    public void beforeStop(BaseMainSupport main) {
    }

    @Override
    public void afterStop(BaseMainSupport main) {
    }

    public static class MyBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("timer:beforeStart")
                    .id("beforeStart")
                    .to("log:beforeStart");
        }
    }
}
