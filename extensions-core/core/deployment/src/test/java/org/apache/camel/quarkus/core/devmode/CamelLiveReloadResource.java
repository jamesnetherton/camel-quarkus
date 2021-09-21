package org.apache.camel.quarkus.core.devmode;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;

@Path("/camel/devmode")
public class CamelLiveReloadResource {

    @Path("/observer/registered")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public boolean isCamelHotReplacementObserverBeanRegistered() {
        InstanceHandle<CamelHotReplacementObserver> instance = Arc.container().instance(CamelHotReplacementObserver.class);
        return instance != null && instance.isAvailable();
    }
}
