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

package org.apache.camel.quarkus.component.kudu.it;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Consumer;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.apache.camel.util.CollectionHelper;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.TestcontainersConfiguration;

import static org.apache.camel.quarkus.component.kudu.it.KuduRoute.KUDU_AUTHORITY_CONFIG_KEY;

public class KuduTestResource implements QuarkusTestResourceLifecycleManager {
    private static final Logger LOG = LoggerFactory.getLogger(KuduTestResource.class);
    private static final int KUDU_MASTER_RPC_PORT = 7051;
    private static final int KUDU_MASTER_HTTP_PORT = 8051;
    private static final int KUDU_TABLET_RPC_PORT = 7050;
    private static final int KUDU_TABLET_HTTP_PORT = 8050;
    private static final int KERBEROS_PORT_1 = 464;
    private static final int KERBEROS_PORT_2 = 749;
    private static final int KERBEROS_PORT_3 = 88;
    private static final String KUDU_IMAGE = ConfigProvider.getConfig().getValue("kudu.container.image", String.class);
    private static final String KUDU_MASTER_NETWORK_ALIAS = "kudu-master";
    private static final String KUDU_TABLET_NETWORK_ALIAS = "kudu-tserver";
    private static final String KUDU_KERBEROS_NETWORK_ALIAS = "kudu-kerberos";

    private GenericContainer<?> kerberosContainer;
    private GenericContainer<?> masterContainer;
    private GenericContainer<?> tabletContainer;

    @Override
    public Map<String, String> start() {
        LOG.info(TestcontainersConfiguration.getInstance().toString());

        Network kuduNetwork = Network.newNetwork();

        // Kerberos server for authentication
        kerberosContainer = new GenericContainer<>("quay.io/jamesnetherton/krb5-server:1.0.0")
                .withExposedPorts(KERBEROS_PORT_1, KERBEROS_PORT_2, KERBEROS_PORT_3)
                .withNetwork(kuduNetwork)
                .withNetworkAliases(KUDU_KERBEROS_NETWORK_ALIAS)
                .withEnv("KRB5_REALM", "KUDU.LOCAL")
                .withEnv("KRB5_KDC", "localhost")
                .withEnv("KRB5_PASS", "test")
                .waitingFor(Wait.forListeningPort());
        kerberosContainer.start();

        try (InputStream krb5Conf = Thread.currentThread().getContextClassLoader().getResourceAsStream("kudu-krb5.conf")) {
            // Setup the Kudu master server container
            String masterAdvertisedAddress = getRpcAdvertisedAddress(KUDU_MASTER_NETWORK_ALIAS, KUDU_MASTER_RPC_PORT);
            masterContainer = new GenericContainer<>(KUDU_IMAGE)
                    .withCommand("master")
                    .withCopyToContainer(Transferable.of(krb5Conf.readAllBytes(), 0640), "/etc/krb5.conf")
                    .withExposedPorts(KUDU_MASTER_RPC_PORT, KUDU_MASTER_HTTP_PORT)
                    .withNetwork(kuduNetwork)
                    .withNetworkAliases(KUDU_MASTER_NETWORK_ALIAS)
                    .withEnv("MASTER_ARGS",
                            "--rpc_authentication=required --keytab_file=/etc/krb5.conf --principal=kudu/kudu --unlock_unsafe_flags=true --rpc_advertised_addresses="
                                    + masterAdvertisedAddress)
                    .withLogConsumer(new Slf4jLogConsumer(LOG))
                    .waitingFor(Wait.forListeningPort());
            masterContainer.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (InputStream krb5Conf = Thread.currentThread().getContextClassLoader().getResourceAsStream("kudu-krb5.conf")) {
            // Setup the Kudu master server container
            String masterAdvertisedAddress = getRpcAdvertisedAddress(KUDU_MASTER_NETWORK_ALIAS, KUDU_MASTER_RPC_PORT);
            masterContainer = new GenericContainer<>(KUDU_IMAGE)
                    .withCommand("master")
                    .withExposedPorts(KUDU_MASTER_RPC_PORT, KUDU_MASTER_HTTP_PORT)
                    .withNetwork(kuduNetwork)
                    .withNetworkAliases(KUDU_MASTER_NETWORK_ALIAS)
                    .withEnv("MASTER_ARGS",
                            "--rpc_authentication=required --keytab_file=/etc/krb5.conf --principal=kudu/kudu --unlock_unsafe_flags=true --rpc_advertised_addresses="
                                    + masterAdvertisedAddress)
                    .withLogConsumer(new Slf4jLogConsumer(LOG))
                    .waitingFor(Wait.forListeningPort());
            masterContainer.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Force host name and port, so that the tablet container is accessible from KuduResource, KuduTest and KuduIT.
        Consumer<CreateContainerCmd> consumer = cmd -> {
            Ports portBindings = new Ports();
            portBindings.bind(ExposedPort.tcp(KUDU_TABLET_RPC_PORT), Ports.Binding.bindPort(KUDU_TABLET_RPC_PORT));
            portBindings.bind(ExposedPort.tcp(KUDU_TABLET_HTTP_PORT), Ports.Binding.bindPort(KUDU_TABLET_HTTP_PORT));
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withPortBindings(portBindings)
                    .withNetworkMode(kuduNetwork.getId());
            cmd.withHostName(KUDU_TABLET_NETWORK_ALIAS).withHostConfig(hostConfig);
        };

        // Setup the Kudu tablet server container
        String tabletAdvertisedAddress = getRpcAdvertisedAddress(KUDU_TABLET_NETWORK_ALIAS, KUDU_TABLET_RPC_PORT);
        tabletContainer = new GenericContainer<>(KUDU_IMAGE)
                .withCommand("tserver")
                .withEnv("KUDU_MASTERS", KUDU_MASTER_NETWORK_ALIAS)
                .withExposedPorts(KUDU_TABLET_RPC_PORT, KUDU_TABLET_HTTP_PORT)
                .withNetwork(kuduNetwork)
                .withNetworkAliases(KUDU_TABLET_NETWORK_ALIAS)
                .withCreateContainerCmdModifier(consumer)
                .withEnv("TSERVER_ARGS",
                        "--unlock_unsafe_flags=true --rpc_advertised_addresses=" + tabletAdvertisedAddress)
                .withLogConsumer(new Slf4jLogConsumer(LOG))
                .waitingFor(Wait.forListeningPort());
        tabletContainer.start();

        // Print interesting Kudu servers connectivity information
        final String masterRpcAuthority = masterContainer.getHost() + ":"
                + masterContainer.getMappedPort(KUDU_MASTER_RPC_PORT);

        LOG.info("Kudu master RPC accessible at " + masterRpcAuthority);
        final String masterHttpAuthority = masterContainer.getHost() + ":"
                + masterContainer.getMappedPort(KUDU_MASTER_HTTP_PORT);
        LOG.info("Kudu master HTTP accessible at " + masterHttpAuthority);
        final String tServerRpcAuthority = tabletContainer.getHost() + ":"
                + tabletContainer.getMappedPort(KUDU_TABLET_RPC_PORT);
        LOG.info("Kudu tablet server RPC accessible at " + tServerRpcAuthority);
        final String tServerHttpAuthority = tabletContainer.getHost() + ":"
                + tabletContainer.getMappedPort(KUDU_TABLET_HTTP_PORT);
        LOG.info("Kudu tablet server HTTP accessible at " + tServerHttpAuthority);

        return CollectionHelper.mapOf(KUDU_AUTHORITY_CONFIG_KEY, masterRpcAuthority);
    }

    @Override
    public void stop() {
        try {
            if (masterContainer != null) {
                masterContainer.stop();
            }
            if (tabletContainer != null) {
                tabletContainer.stop();
            }
        } catch (Exception ex) {
            LOG.error("An issue occurred while stopping the KuduTestResource", ex);
        }
    }

    String getRpcAdvertisedAddress(String host, int port) {
        String addressFormat = "%s:%d";
        String dockerHost = DockerClientFactory.instance().dockerHostIpAddress();
        if (dockerHost.equals("localhost") || dockerHost.equals("127.0.0.1")) {
            return addressFormat.formatted(host, port);
        } else {
            return addressFormat.formatted(dockerHost, port);
        }
    }
}
