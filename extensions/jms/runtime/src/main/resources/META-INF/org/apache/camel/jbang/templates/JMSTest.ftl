package [=package];

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class JMSTest extends CamelQuarkusTestSupport {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ConfigProperty(name = "quarkus.http.test-port")
    String port;

    @Test
    void testBooksAPI() throws Exception {
        String bookJson = """
            {
                "id": "1",
                "title": "Camel In Action"
            }
        """;

        // Generate a JMS message
        template.sendBody("http://localhost:" + port + "/books", bookJson.trim());

        // Wait for the message to be consumed
        Thread.sleep(250);

        // Get results
        String getResult = template.requestBody("http://localhost:" + port + "/books", null, String.class);
        List<Book> books = MAPPER.readValue(getResult, new TypeReference<>(){});
        assertEquals(1, books.size());

        Book book = books.get(0);
        assertEquals(1, book.getId());
        assertEquals("Camel In Action", book.getTitle());
    }
}