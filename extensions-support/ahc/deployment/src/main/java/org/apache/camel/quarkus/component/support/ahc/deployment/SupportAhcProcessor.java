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
package org.apache.camel.quarkus.component.support.ahc.deployment;

import java.util.stream.Stream;

import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.pkg.steps.NativeBuild;
import io.quarkus.gizmo.ClassCreator;
import org.apache.camel.quarkus.component.support.ahc.runtime.graal.NettyIoUringAbsent;

class SupportAhcProcessor {

    private static final String FEATURE = "camel-support-ahc";
    private static final String IO_URING_EVENT_LOOP_GROUP_CLASS_NAME = "io.netty.incubator.channel.uring.IOUringEventLoopGroup";
    private static final String IO_URING_SOCKET_CHANNEL_CLASS_NAME = "io.netty.incubator.channel.uring.IOUringSocketChannel";

    @BuildStep
    NativeImageResourceBuildItem nativeImageResources() {
        return new NativeImageResourceBuildItem(
                "org/asynchttpclient/config/ahc-default.properties",
                "org/asynchttpclient/config/ahc.properties");
    }

    @BuildStep
    ExtensionSslNativeSupportBuildItem activateSslNativeSupport() {
        return new ExtensionSslNativeSupportBuildItem(FEATURE);
    }

    @BuildStep(onlyIfNot = NettyIoUringAbsent.class)
    RuntimeInitializedClassBuildItem runtimeInitializedClasses() {
        return new RuntimeInitializedClassBuildItem("io.netty.incubator.channel.uring.IOUringEventLoopGroup");
    }

    @BuildStep(onlyIf = { NativeBuild.class, NettyIoUringAbsent.class })
    void ioUringGeneratedClasses(BuildProducer<GeneratedClassBuildItem> generatedClass) {
        // Generate skeleton io_uring classes to make native compilation work
        Stream.of(IO_URING_EVENT_LOOP_GROUP_CLASS_NAME, IO_URING_SOCKET_CHANNEL_CLASS_NAME).forEach(className -> {
            ClassCreator.builder()
                    .className(className)
                    .superClass(Object.class)
                    .setFinal(true)
                    .classOutput(new GeneratedClassGizmoAdaptor(generatedClass, false))
                    .build()
                    .close();
        });
    }

    @BuildStep
    void runtimeInitializedClasses(BuildProducer<RuntimeInitializedClassBuildItem> runtimeInitializedClass) {
        Stream.of("org.asynchttpclient.netty.channel.ChannelManager",
                "org.asynchttpclient.netty.request.NettyRequestSender",
                "org.asynchttpclient.RequestBuilderBase",
                "org.asynchttpclient.resolver.RequestHostnameResolver",
                "org.asynchttpclient.ntlm.NtlmEngine")
                .map(RuntimeInitializedClassBuildItem::new)
                .forEach(runtimeInitializedClass::produce);
    }
}
