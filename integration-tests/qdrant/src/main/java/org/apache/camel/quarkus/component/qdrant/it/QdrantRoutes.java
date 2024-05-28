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
package org.apache.camel.quarkus.component.qdrant.it;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.qdrant.Qdrant;
import org.apache.camel.component.qdrant.QdrantAction;
import org.apache.camel.spi.DataType;

public class QdrantRoutes extends RouteBuilder {
    public static final String TEST_COLLECTION_NAME = "testCollection";
    public static final long TEST_POINTS_ID = 8;
    public static final long TEST_COLLECTION_SIZE = 2;

    public static final String EMBEDDINGS_COLLECTION_NAME = "testEmbeddings";
    public static final long EMBEDDINGS_POINTS_ID = 999;
    public static final long EMBEDDINGS_POINTS_SIZE = 384;

    @Override
    public void configure() throws Exception {
        from("direct:embeddings")
                .to("langchain4j-embeddings:qdrant")
                .setHeader(Qdrant.Headers.ACTION).constant(QdrantAction.UPSERT)
                .setHeader(Qdrant.Headers.POINT_ID).constant(EMBEDDINGS_POINTS_ID)
                .transform(new DataType("qdrant:embeddings"))
                .toF("qdrant:%s", EMBEDDINGS_COLLECTION_NAME);
    }
}
