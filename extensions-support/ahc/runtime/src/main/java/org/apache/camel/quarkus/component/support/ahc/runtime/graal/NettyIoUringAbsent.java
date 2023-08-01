package org.apache.camel.quarkus.component.support.ahc.runtime.graal;

import java.util.function.BooleanSupplier;

public class NettyIoUringAbsent implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        try {
            Class.forName("io.netty.incubator.channel.uring.IOUring");
            return false;
        } catch (ClassNotFoundException e) {
            return true;
        }
    }
}
