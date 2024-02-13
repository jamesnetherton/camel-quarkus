package org.apache.camel.quarkus.component.kudu.it;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.apache.kudu.test.KuduTestHarness;
import org.apache.kudu.test.cluster.MiniKuduCluster.MiniKuduClusterBuilder;

import static org.apache.camel.quarkus.component.kudu.it.KuduRoute.KUDU_AUTHORITY_CONFIG_KEY;

public class SecureKuduTestResource implements QuarkusTestResourceLifecycleManager {
    private KuduTestHarness kuduTestHarness;

    @Override
    public Map<String, String> start() {
        try {
            MiniKuduClusterBuilder clusterBuilder = KuduTestHarness.getBaseClusterBuilder()
                    .enableKerberos();

            kuduTestHarness = new KuduTestHarness(clusterBuilder);
            kuduTestHarness.before();

            return Map.of(KUDU_AUTHORITY_CONFIG_KEY, kuduTestHarness.getMasterAddressesAsString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void inject(Object testInstance) {
        if (kuduTestHarness != null) {
            SecureKuduTest test = (SecureKuduTest) testInstance;
            test.setKuduClient(kuduTestHarness.getClient());
        }
    }

    @Override
    public void stop() {
        if (kuduTestHarness != null) {
            kuduTestHarness.after();
        }
    }
}
