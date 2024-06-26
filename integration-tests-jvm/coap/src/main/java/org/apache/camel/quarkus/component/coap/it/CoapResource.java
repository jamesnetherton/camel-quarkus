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
package org.apache.camel.quarkus.component.coap.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.camel.CamelContext;
import org.jboss.logging.Logger;

@Path("/coap")
@ApplicationScoped
public class CoapResource {

    private static final Logger LOG = Logger.getLogger(CoapResource.class);

    private static final String COMPONENT_COAP = "coap";
    private static final String COMPONENT_COAP_TCP = "coap+tcp";
    private static final String COMPONENT_COAPS = "coaps";
    private static final String COMPONENT_COAPS_TCP = "coaps+tcp";
    @Inject
    CamelContext context;

    @Path("/load/component/coap")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response loadComponentCoap() throws Exception {
        /* This is an autogenerated test */
        if (context.getComponent(COMPONENT_COAP) != null) {
            return Response.ok().build();
        }
        LOG.warnf("Could not load [%s] from the Camel context", COMPONENT_COAP);
        return Response.status(500, COMPONENT_COAP + " could not be loaded from the Camel context").build();
    }

    @Path("/load/component/coap-tcp")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response loadComponentCoapTcp() throws Exception {
        /* This is an autogenerated test */
        if (context.getComponent(COMPONENT_COAP_TCP) != null) {
            return Response.ok().build();
        }
        LOG.warnf("Could not load [%s] from the Camel context", COMPONENT_COAP_TCP);
        return Response.status(500, COMPONENT_COAP_TCP + " could not be loaded from the Camel context").build();
    }

    @Path("/load/component/coaps")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response loadComponentCoaps() throws Exception {
        /* This is an autogenerated test */
        if (context.getComponent(COMPONENT_COAPS) != null) {
            return Response.ok().build();
        }
        LOG.warnf("Could not load [%s] from the Camel context", COMPONENT_COAPS);
        return Response.status(500, COMPONENT_COAPS + " could not be loaded from the Camel context").build();
    }

    @Path("/load/component/coaps-tcp")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response loadComponentCoapsTcp() throws Exception {
        /* This is an autogenerated test */
        if (context.getComponent(COMPONENT_COAPS_TCP) != null) {
            return Response.ok().build();
        }
        LOG.warnf("Could not load [%s] from the Camel context", COMPONENT_COAPS_TCP);
        return Response.status(500, COMPONENT_COAPS_TCP + " could not be loaded from the Camel context").build();
    }
}
