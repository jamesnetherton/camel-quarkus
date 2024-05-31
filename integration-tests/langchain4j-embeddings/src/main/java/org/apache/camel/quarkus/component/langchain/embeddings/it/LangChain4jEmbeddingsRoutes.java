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
package org.apache.camel.quarkus.component.langchain.embeddings.it;

import org.apache.camel.builder.RouteBuilder;

public class LangChain4jEmbeddingsRoutes extends RouteBuilder {
    @Override
    public void configure() throws Exception {
//        System.load("/Users/james/.djl.ai/tokenizers/0.15.0-0.26.0-osx-aarch64/libtokenizers.dylib");

        from("direct:start")
                .to("langchain4j-embeddings:create-from-text");
    }
}
