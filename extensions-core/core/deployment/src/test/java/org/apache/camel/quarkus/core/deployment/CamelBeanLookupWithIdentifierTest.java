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
package org.apache.camel.quarkus.core.deployment;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import java.util.Properties;

import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Produce;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.CamelContextHelper;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CamelBeanLookupWithIdentifierTest {

    @RegisterExtension
    static final QuarkusUnitTest CONFIG = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(applicationProperties(), "application.properties"));

    public static Asset applicationProperties() {
        Writer writer = new StringWriter();

        Properties props = new Properties();
        props.setProperty("quarkus.banner.enabled", "false");

        try {
            props.store(writer, "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new StringAsset(writer.toString());
    }

    @Inject
    CamelContext context;

    @Produce("direct:start")
    FluentProducerTemplate template;

    @Test
    public void beanLookupWithIdentifierAnnotation() {
        MyIdentifiedBean bean = CamelContextHelper.lookup(context, "my-identifier", MyIdentifiedBean.class);
        assertNotNull(bean);
        assertEquals("Hello World", template.request(String.class));
    }

    @Test
    void resolveIdentifierWithName() {
        Map<String, MyIdentifiedBean> typeWithName = context.getRegistry().findByTypeWithName(MyIdentifiedBean.class);
        assertNotNull(typeWithName);
        assertEquals(1, typeWithName.size());
        assertNotNull(typeWithName.get("my-identifier"));
    }

    @Produces
    @Identifier("my-identifier")
    public MyIdentifiedBean createIdentifiedBean() {
        return new MyIdentifiedBean();
    }

    public static class Routes extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("direct:start").bean("my-identifier");
        }
    }

    public static class MyIdentifiedBean {
        public String greet() {
            return "Hello World";
        }
    }
}
