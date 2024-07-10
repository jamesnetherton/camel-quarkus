[#if dependencyIsPresent.apply("com.ibm.mq.jakarta.client")]
package [=package];

import com.ibm.mq.jakarta.jms.MQConnectionFactory;
import com.ibm.mq.jakarta.jms.MQXAConnectionFactory;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import io.quarkiverse.messaginghub.pooled.jms.PooledJmsWrapper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.inject.Produces;
import jakarta.jms.ConnectionFactory;
import org.eclipse.microprofile.config.inject.ConfigProperty;

public class IBMMQConnectionFactory {
    @ConfigProperty(name = "ibm.mq.host")
    String host;

    @ConfigProperty(name = "ibm.mq.port")
    int port;

    @ConfigProperty(name = "ibm.mq.channel")
    String channel;

    @ConfigProperty(name = "ibm.mq.queueManagerName")
    String queueManagerName;

    @ConfigProperty(name = "ibm.mq.user")
    String user;

    @ConfigProperty(name = "ibm.mq.password")
    String password;

    @Produces
    public ConnectionFactory createConnectionFactory(PooledJmsWrapper wrapper) {
        MQConnectionFactory mq = new MQConnectionFactory();
        setupMQ(mq);
        return wrapper.wrapConnectionFactory(mq);
    }

    private void setupMQ(MQConnectionFactory mq) {
        try {
            mq.setHostName(host);
            mq.setPort(port);
            mq.setChannel(channel);
            mq.setQueueManager(queueManagerName);
            mq.setTransportType(WMQConstants.WMQ_CM_CLIENT);
            mq.setStringProperty(WMQConstants.USERID, user);
            mq.setStringProperty(WMQConstants.PASSWORD, password);
        } catch (Exception e) {
            throw new RuntimeException("Unable to create new IBM MQ connection factory", e);
        }
    }
}
[/#if]