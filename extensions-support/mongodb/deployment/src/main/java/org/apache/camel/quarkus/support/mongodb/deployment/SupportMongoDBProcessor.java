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
package org.apache.camel.quarkus.support.mongodb.deployment;

import java.util.List;

import com.mongodb.client.MongoClient;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.mongodb.deployment.MongoClientNameBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelBeanQualifierResolverBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeBeanBuildItem;
import org.apache.camel.quarkus.support.mongodb.CamelMongoDBSupportRecorder;

class SupportMongoDBProcessor {
    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    void registerCamelMongoClientProducers(
            List<MongoClientNameBuildItem> mongoClientNames,
            BuildProducer<CamelBeanQualifierResolverBuildItem> camelBeanQualifierResolver,
            CamelMongoDBSupportRecorder recorder) {

        for (MongoClientNameBuildItem mongoClientNameBuildItem : mongoClientNames) {
            if (mongoClientNameBuildItem.isAddQualifier()) {
                camelBeanQualifierResolver.produce(
                        new CamelBeanQualifierResolverBuildItem(
                                MongoClient.class,
                                recorder.createMongoClientNameQualifierResolver(mongoClientNameBuildItem.getName())));
            }
        }
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void bindDefaultMongoClientToRegistry(
            BuildProducer<CamelRuntimeBeanBuildItem> camelRuntimeBean,
            CamelMongoDBSupportRecorder recorder) {

        camelRuntimeBean.produce(
                new CamelRuntimeBeanBuildItem("camelMongoClient",
                        MongoClient.class.getTypeName(),
                        recorder.getDefaultMongoClient()));
    }
}
