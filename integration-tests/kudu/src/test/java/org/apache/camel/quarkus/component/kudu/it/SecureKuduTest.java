package org.apache.camel.quarkus.component.kudu.it;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.kudu.client.KuduClient;
import org.apache.kudu.client.KuduException;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(SecureKuduTestResource.class)
public class SecureKuduTest {
    private KuduClient client;

    public void setKuduClient(KuduClient client) {
        this.client = client;
    }

    @Test
    void testIt() throws KuduException {
        this.client.deleteTable("foo");
    }
}
