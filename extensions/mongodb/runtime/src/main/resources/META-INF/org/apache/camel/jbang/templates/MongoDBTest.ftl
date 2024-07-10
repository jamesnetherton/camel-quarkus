package [=package];

import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class MongoDBTest extends CamelQuarkusTestSupport {
    @ConfigProperty(name = "quarkus.http.test-port")
    String port;

    @Test
    void testBooksAPI() {
        String bookJson = """
            {
                "_id": "1",
                "title": "Camel In Action"
            }
        """;

        // Insert
        template.sendBody("http://localhost:" + port + "/books", bookJson.trim());

        // Get
        String getResult = template.requestBody("http://localhost:" + port + "/books?id=1", null, String.class);
        Document getResultDocument = Document.parse(getResult);
        assertEquals("1", getResultDocument.get("_id"));
        assertEquals("Camel In Action", getResultDocument.get("title"));

        // Update
        template.sendBody("http://localhost:" + port + "/books?httpMethod=PATCH&id=1&title=Camel In Action 2", null);
        String updateResult = template.requestBody("http://localhost:" + port + "/books?id=1", null, String.class);
        Document updateResultDocument = Document.parse(updateResult);
        assertEquals("1", updateResultDocument.get("_id"));
        assertEquals("Camel In Action 2", updateResultDocument.get("title"));

        // Delete
        template.sendBody("http://localhost:" + port + "/books?httpMethod=DELETE&id=1", null);
    }
}
