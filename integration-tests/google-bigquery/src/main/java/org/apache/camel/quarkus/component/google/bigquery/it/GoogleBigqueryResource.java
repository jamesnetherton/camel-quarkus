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
package org.apache.camel.quarkus.component.google.bigquery.it;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.cloud.NoCredentials;
import com.google.cloud.ServiceOptions;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.http.HttpTransportOptions;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConnectionFactory;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/google-bigquery")
public class GoogleBigqueryResource {

    public static final String DATASET_ID = "test";
    public static final String TABLE_NAME = "test";

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    @ConfigProperty(name = "google.project.id", defaultValue = "test")
    String projectId;

    String tableId = DATASET_ID + "." + TABLE_NAME;

    @Named("bigQueryConnectionFactory")
    public GoogleBigQueryConnectionFactory bigQueryConnectionFactory() {
        Config config = ConfigProvider.getConfig();
        Optional<String> host = config.getOptionalValue("google.bigquery.host", String.class);

        if (host.isPresent()) {
            return new GoogleBigQueryConnectionFactory() {
                @Override
                public synchronized BigQuery getDefaultClient() throws Exception {
                    HttpTransportOptions.Builder builder = HttpTransportOptions.newBuilder();
                    HttpTransportOptions options = new HttpTransportOptions(builder) {

                        @Override
                        public HttpRequestInitializer getHttpRequestInitializer(ServiceOptions<?, ?> serviceOptions) {
                            return new HttpRequestInitializer() {
                                public void initialize(HttpRequest httpRequest) throws IOException {
                                    httpRequest.setInterceptor(new HttpExecuteInterceptor() {
                                        @Override
                                        public void intercept(HttpRequest request) throws IOException {
                                            String encoding = request.getHeaders().getAcceptEncoding();
                                            if (encoding != null && encoding.equals("gzip")) {
                                                request.setEncoding(null);
                                            }
                                        }
                                    });
                                }
                            };
                        }
                    };

                    return BigQueryOptions.newBuilder()
                            .setCredentials(NoCredentials.getInstance())
                            .setHost(host.get())
                            .setLocation(host.get())
                            .setProjectId(projectId)
                            .setTransportOptions(options)
                            .build()
                            .getService();
                }
            };
        }

        return null;
    }

    @Path("/table")
    @POST
    public Response createTable() {
        String sql = "CREATE TABLE `" + tableId + "` (id NUMERIC, col1 STRING, col2 STRING)";
        producerTemplate.requestBody("google-bigquery-sql:" + projectId + ":" + sql, null,
                Long.class);
        return Response.created(URI.create("https://camel.apache.org")).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response insertRow(Map<String, String> tableData) {
        producerTemplate.requestBody("google-bigquery:" + projectId + ":" + DATASET_ID + ":" + TABLE_NAME, tableData);
        return Response.created(URI.create("https://camel.apache.org")).build();
    }

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRow() {
        String sql = "SELECT * FROM `" + "test" + "`";
        Long rowCount = producerTemplate.requestBody("google-bigquery-sql:" + projectId + ":" + sql, null, Long.class);
        return Response.ok(rowCount).build();
    }

    @Path("/file")
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRowUsingQueryResource() throws IOException {
        String sql = "SELECT * FROM `" + tableId + "`";
        java.nio.file.Path path = Files.createTempDirectory("bigquery");
        java.nio.file.Path sqlFile = Files.createTempFile(path, "bigquery", ".sql");
        Files.write(sqlFile, sql.getBytes(StandardCharsets.UTF_8));

        Long rowCount = producerTemplate.requestBody(
                "google-bigquery-sql:" + projectId + ":file:" + sqlFile.toAbsolutePath().toString(),
                null, Long.class);
        return Response.ok(rowCount).build();
    }

    @Path("/table")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response dropTable() {
        String sql = "DROP TABLE `" + tableId + "`";
        producerTemplate.requestBody("google-bigquery-sql:" + projectId + ":" + sql, null, Long.class);
        return Response.ok().build();
    }
}
