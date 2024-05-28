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

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.apache.camel.quarkus.component.qdrant.it.QdrantRoutes.EMBEDDINGS_COLLECTION_NAME;
import static org.apache.camel.quarkus.component.qdrant.it.QdrantRoutes.EMBEDDINGS_POINTS_SIZE;
import static org.apache.camel.quarkus.component.qdrant.it.QdrantRoutes.TEST_COLLECTION_NAME;
import static org.apache.camel.quarkus.component.qdrant.it.QdrantRoutes.TEST_COLLECTION_SIZE;
import static org.apache.camel.quarkus.component.qdrant.it.QdrantRoutes.TEST_POINTS_ID;
import static org.hamcrest.Matchers.is;

@QuarkusTestResource(QdrantTestResource.class)
@QuarkusTest
class QdrantTest {

    @Test
    public void createUpsertRetrieveAndDeleteShouldSucceed() {
        RestAssured.given()
                .queryParam("collectionName", TEST_COLLECTION_NAME)
                .queryParam("collectionSize", TEST_COLLECTION_SIZE)
                .put("/qdrant/createCollection")
                .then()
                .statusCode(200);

        RestAssured.given()
                .queryParam("collectionName", TEST_COLLECTION_NAME)
                .put("/qdrant/upsert")
                .then()
                .statusCode(200);

        RestAssured.given()
                .queryParam("collectionName", TEST_COLLECTION_NAME)
                .queryParam("pointsId", TEST_POINTS_ID)
                .get("/qdrant/retrieve")
                .then()
                .statusCode(200)
                .body(is("1/io.qdrant.client.grpc.Points$RetrievedPoint"));

        RestAssured.given()
                .queryParam("collectionName", TEST_COLLECTION_NAME)
                .delete("/qdrant/delete")
                .then()
                .statusCode(200)
                .body(is("1/Completed/2"));

        RestAssured.given()
                .queryParam("collectionName", TEST_COLLECTION_NAME)
                .get("/qdrant/retrieve")
                .then()
                .statusCode(200)
                .body(is("0/"));
    }

    @Test
    public void langChain4jEmbeddings() {
        RestAssured.given()
                .queryParam("collectionName", EMBEDDINGS_COLLECTION_NAME)
                .queryParam("collectionSize", EMBEDDINGS_POINTS_SIZE)
                .put("/qdrant/createCollection")
                .then()
                .statusCode(200);

        RestAssured.given()
                .queryParam("collectionName", EMBEDDINGS_COLLECTION_NAME)
                .body("Hello World")
                .post("/qdrant/embeddings")
                .then()
                .statusCode(200);

        RestAssured.given()
                .queryParam("collectionName", EMBEDDINGS_COLLECTION_NAME)
                .queryParam("pointsId", QdrantRoutes.EMBEDDINGS_POINTS_ID)
                .get("/qdrant/retrieve")
                .then()
                .statusCode(200)
                .body(is("1/io.qdrant.client.grpc.Points$RetrievedPoint"));
    }
}
