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
package org.apache.camel.quarkus.component.reactive.streams.it;

import org.apache.camel.builder.RouteBuilder;

public class ReactiveStreamsRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("direct:toUpper")
                .routeId("toUpper")
                .setBody().body(String.class, s -> s.toUpperCase())
                .to("reactive-streams:toUpper?backpressureStrategy=BUFFER");

        from("timer:streamEvents?includeMetadata=true&repeatCount=5&period=100")
                .routeId("streamEvents")
                .autoStartup(false)
                .setBody().simple("event-${header.CamelTimerCounter}")
                .log("Sending to queue: ${body}")
                .to("seda:streamQueue");

        from("timer:namedStreamEvents?includeMetadata=true&repeatCount=3&period=500&delay=500")
                .routeId("namedStreamEvents")
                .autoStartup(false)
                .setBody().simple("named-${header.CamelTimerCounter}")
                .log("Sending to named stream: ${body}")
                .to("reactive-streams:namedStream?backpressureStrategy=BUFFER");

        from("timer:exchangeStreamEvents?includeMetadata=true&repeatCount=3&period=500&delay=500")
                .routeId("exchangeStreamEvents")
                .autoStartup(false)
                .setBody().simple("exchange-${header.CamelTimerCounter}")
                .log("Sending to exchange stream: ${body}")
                .to("reactive-streams:exchangeStream?backpressureStrategy=BUFFER");
    }
}
