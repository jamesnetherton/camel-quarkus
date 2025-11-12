package org.apache.camel.quarkus.component.opentelemetry2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.Router;

@Recorder
public class OpenTelemetry2Recorder {
    public void configureVertxWebTracePropagation(RuntimeValue<Router> httpRouter) {
        TextMapSetter<MultiMap> textMapSetter = (carrier, key, value) -> {
            if (carrier != null && key != null && value != null) {
                carrier.set(key, value);
            }
        };

        httpRouter.getValue().route().handler(routingContext -> {
            GlobalOpenTelemetry.getPropagators()
                    .getTextMapPropagator()
                    .inject(Context.current(), routingContext.request().headers(), textMapSetter);

            routingContext.next();
        });
    }
}
