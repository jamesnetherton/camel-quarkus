[#if dependencyIsPresent.apply("quarkus-artemis-jms")]
quarkus.artemis.devservices.enabled = true
[/#if]
[#if dependencyIsPresent.apply("com.ibm.mq.jakarta.client")]
# IBM MQ container configuration options
# https://github.com/ibm-messaging/mq-container/blob/master/docs/developer-config.md
jms.queue.name: DEV.QUEUE.1
ibm.mq.host = localhost
ibm.mq.port = 1414
ibm.mq.channel = DEV.APP.SVRCONN
ibm.mq.queueManagerName = QM1
ibm.mq.user = app
ibm.mq.password = passw0rd
[#else]
jms.queue.name = books-queue
[/#if]
