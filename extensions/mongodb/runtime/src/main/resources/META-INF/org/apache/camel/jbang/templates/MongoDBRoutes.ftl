package [=package];

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.bson.conversions.Bson;

public class MongoDBRoutes extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        // REST books API
        rest("/books")
            // curl localhost:8080/books?id=1
            .get()
                .to("direct:findById")

            // curl -d '{"_id": "1", "title": "Camel In Action"}' -H 'Content-Type: text/plain' localhost:8080/books
            .post()
                .to("direct:insert")

            // curl -X PATCH localhost:8080/books?id=1&title=Camel In Action 2
            .patch()
                .to("direct:update")

            // curl -X DELETE localhost:8080/books?id=1
            .delete()
                .to("direct:delete");

        // Finds a document by id
        from("direct:findById")
            .log("Fetching document for id ${header.id}")
            .setBody().header("id")
            .to("mongodb:%s?database={{quarkus.mongodb.database}}&collection={{mongodb.collection}}&operation=findById&dynamicity=true")
            .setBody().simple("${body.toJson()}");

        // Inserts a document
        from("direct:insert")
            .log("Inserting data ${body}")
            .to("mongodb:%s?database={{quarkus.mongodb.database}}&collection={{mongodb.collection}}&operation=insert&dynamicity=true&outputType=Document")
            .log("Inserted document with id ${header.CamelMongoOid}")
            .setBody().simple("${body.toJson()}");

        // Updates a document
        from("direct:update")
            .log("Updating document id = ${header.id}, title = ${header.title}")
            .process(new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                    String id = exchange.getMessage().getHeader("id", String.class);
                    String title = exchange.getMessage().getHeader("title", String.class);
                    Bson eq = Filters.eq("_id", id);
                    Bson set = Updates.set("title", title);
                    exchange.getMessage().setBody(new Bson[] { eq, set });
                }
            })
            .to("mongodb:%s?database={{quarkus.mongodb.database}}&collection={{mongodb.collection}}&operation=update&dynamicity=true&outputType=Document")
            .log("Updated document with id ${header.id}")
            .setBody().header("id")
            .to("mongodb:%s?database={{quarkus.mongodb.database}}&collection={{mongodb.collection}}&operation=findById&dynamicity=true")
            .setBody().simple("${body.toJson()}");

        // Deletes a document
        from("direct:delete")
            .log("Deleting document for id ${header.id}")
            .process(new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                    String id = exchange.getMessage().getHeader("id", String.class);
                    exchange.getMessage().setBody(Filters.eq("_id", id));
                }
            })
            .to("mongodb:%s?database={{quarkus.mongodb.database}}&collection={{mongodb.collection}}&operation=remove")
            .log("Deleted document with id ${header.id}")
            .setBody().constant(null);
    }
}

