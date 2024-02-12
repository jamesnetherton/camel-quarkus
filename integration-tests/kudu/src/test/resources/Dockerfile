FROM docker.io/apache/kudu:1.17.0
ADD kudu-entrypoint.sh /kudu-entrypoint.sh
ADD kudu-krb5.conf /etc/krb5.conf
ADD kudu.keytab /kudu.keytab
USER root
RUN chown kudu:kudu /kudu-entrypoint.sh && \
    chmod +x /kudu-entrypoint.sh && \
    chmod 0644 /etc/krb5.conf && \
    chmod +r /etc/krb5.conf && \
    chmod 0640 /kudu.keytab && chown kudu:kudu /kudu.keytab
USER kudu
