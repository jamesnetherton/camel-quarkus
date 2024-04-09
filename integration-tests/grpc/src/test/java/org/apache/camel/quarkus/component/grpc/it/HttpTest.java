package org.apache.camel.quarkus.component.grpc.it;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import org.junit.jupiter.api.Test;

public class HttpTest {

    static String FILE = "/Users/james/Projects/camel-quarkus/integration-tests/grpc/streamingframe.bin";

    @Test
    void sendRequest() {
        Vertx vertx = null;
        try {
            vertx = Vertx.vertx();
            HttpClientOptions httpClientOptions = new HttpClientOptions();
            httpClientOptions.setProtocolVersion(HttpVersion.HTTP_2);
            httpClientOptions.setHttp2ClearTextUpgrade(false);
            HttpClient httpClient = vertx.createHttpClient(httpClientOptions);
            try {
                RequestOptions options = new RequestOptions();
                options.setHost("localhost");
                options.setPort(8000);
                options.setURI("/org.apache.camel.quarkus.component.grpc.it.model.PingPong/PingSyncSync");
                options.addHeader("content-type", "application/grpc");
                options.addHeader("pingid", "100");
                options.addHeader("TE", "trailers");
                options.addHeader("foo", "a".repeat(20000));
                options.setMethod(HttpMethod.POST);

                Vertx finalVertx = vertx;
                httpClient.request(options).compose(request -> {
                    Buffer buffer = finalVertx.fileSystem().readFileBlocking(FILE);
                    request.putHeader("Content-Length", String.valueOf(buffer.length()));
                    request.write(buffer);
                    request.end();
                    return request.response().compose(HttpClientResponse::body);
                })
                        .onSuccess(body -> System.out.println("Got data " + body.toString("ISO-8859-1")))
                        .onFailure(Throwable::printStackTrace);

                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                httpClient.close();
            }
        } finally {
            if (vertx != null) {
                vertx.close();
            }
        }
    }
}
