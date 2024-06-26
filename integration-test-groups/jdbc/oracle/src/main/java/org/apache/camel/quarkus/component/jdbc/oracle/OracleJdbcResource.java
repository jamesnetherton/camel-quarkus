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
package org.apache.camel.quarkus.component.jdbc.oracle;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.quarkus.test.support.jdbc.BaseCamelJdbcResource;
import org.apache.camel.quarkus.test.support.jdbc.model.Camel;

@Path("/test/oracle")
@ApplicationScoped
@RegisterForReflection(targets = { BaseCamelJdbcResource.class, Camel.class })
public class OracleJdbcResource extends BaseCamelJdbcResource {
    @Inject
    @DataSource("oracle")
    AgroalDataSource oracleDataSource;

    @Inject
    ProducerTemplate template;

    @Inject
    CamelContext context;
    String dbKind = "oracle";

    @PostConstruct
    void postConstruct() throws Exception {
        super.initialize(oracleDataSource, dbKind, context, template);
    }
}
