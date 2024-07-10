package [=package];

import org.apache.camel.builder.RouteBuilder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class JMSRoutes extends RouteBuilder {
    private static final Set<Book> BOOKS = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void configure() throws Exception {
        // REST books API
        rest("/books")
            // curl localhost:8080/books
            .get()
                .to("direct:getBooks")

            // curl -d '{"id": "1", "title": "Camel In Action"}' -H 'Content-Type: application/json' localhost:8080/books
            .post()
                .to("direct:addBook");

        // Places the name of a book on a message queue
        from("direct:addBook")
            .log("Producing message to queue {{jms.queue.name}} with body ${body}")
            .to("jms:queue:{{jms.queue.name}}?exchangePattern=InOnly");

        // Retrieves books from the message queue
        from("jms:queue:{{jms.queue.name}}")
                .log("Consumed book ${body} from queue {{jms.queue.name}}")
                .unmarshal().json(Book.class)
                .process(exchange -> BOOKS.add(exchange.getMessage().getBody(Book.class)));

        from("direct:getBooks")
                .setBody().constant(BOOKS)
                .marshal().json();
    }
}