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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.component.qdrant.Qdrant;
import org.apache.camel.component.qdrant.QdrantAction;

@Path("/qdrant")
@ApplicationScoped
public class QdrantResource {

    @Inject
    FluentProducerTemplate producer;

    @Singleton
    @Named
    EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Path("/createCollection")
    @PUT
    @Produces(MediaType.TEXT_PLAIN)
    public Response createCollection(
            @QueryParam("collectionName") String collectionName,
            @QueryParam("collectionSize") long collectionSize) {

        producer.to("qdrant:" + collectionName)
                .withHeader(Qdrant.Headers.ACTION, QdrantAction.CREATE_COLLECTION)
                .withBody(
                        Collections.VectorParams.newBuilder()
                                .setSize(collectionSize)
                                .setDistance(Collections.Distance.Cosine).build())
                .request();

        return Response.ok().build();
    }

    @Path("/upsert")
    @PUT
    @Produces(MediaType.TEXT_PLAIN)
    public Response upsert(@QueryParam("collectionName") String collectionName) {

        producer.to("qdrant:" + collectionName)
                .withHeader(Qdrant.Headers.ACTION, QdrantAction.UPSERT)
                .withBody(
                        Points.PointStruct.newBuilder()
                                .setId(PointIdFactory.id(8))
                                .setVectors(VectorsFactory.vectors(List.of(3.5f, 4.5f)))
                                .putAllPayload(Map.of(
                                        "foo", ValueFactory.value("hello"),
                                        "bar", ValueFactory.value(1)))
                                .build())
                .request();

        return Response.ok().build();
    }

    @Path("/retrieve")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response retrieve(
            @QueryParam("collectionName") String collectionName,
            @QueryParam("pointsId") long pointsId) {
        Exchange exchange = producer.to("qdrant:" + collectionName)
                .withHeader(Qdrant.Headers.ACTION, QdrantAction.RETRIEVE)
                .withBody(PointIdFactory.id(pointsId))
                .request(Exchange.class);

        Collection<?> retrieved = exchange.getIn().getBody(Collection.class);
        String classes = retrieved.stream().map(e -> e.getClass().getName()).distinct().collect(Collectors.joining("/"));
        return Response.ok(Integer.toString(retrieved.size()) + "/" + classes).build();
    }

    @Path("/delete")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response delete(@QueryParam("collectionName") String collectionName) {
        Exchange exchange = producer.to("qdrant:" + collectionName)
                .withHeader(Qdrant.Headers.ACTION, QdrantAction.DELETE)
                .withBody(ConditionFactory.matchKeyword("foo", "hello"))
                .request(Exchange.class);

        Object operationId = exchange.getIn().getHeader(Qdrant.Headers.OPERATION_ID);
        Object opeartionStatus = exchange.getIn().getHeader(Qdrant.Headers.OPERATION_STATUS);
        Object operationValue = exchange.getIn().getHeader(Qdrant.Headers.OPERATION_STATUS_VALUE);

        return Response.ok(operationId + "/" + opeartionStatus + "/" + operationValue).build();
    }

    @Path("/embeddings")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public Response embeddings(String text) {
        producer.to("direct:embeddings")
                .withBody(text)
                .request();
        return Response.ok().build();
    }
}
