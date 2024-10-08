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
package org.apache.camel.quarkus.component.knative.channel.consumer.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.component.knative.KnativeComponent;

@Path("/knative-channel-consumer")
@ApplicationScoped
public class KnativeChannelConsumerResource {
    @Inject
    ConsumerTemplate consumerTemplate;

    @Inject
    CamelContext context;

    @GET
    public String readReceivedMessages() {
        return consumerTemplate.receiveBody("seda:queue-channel", 1000, String.class);
    }

    @GET
    @Path("inspect")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject inspect() {
        var component = context.getComponent("knative", KnativeComponent.class);
        var builder = Json.createObjectBuilder();

        if (component.getConsumerFactory() != null) {
            builder.add("consumer-factory", component.getConsumerFactory().getClass().getName());
        }

        return builder.build();
    }

}
