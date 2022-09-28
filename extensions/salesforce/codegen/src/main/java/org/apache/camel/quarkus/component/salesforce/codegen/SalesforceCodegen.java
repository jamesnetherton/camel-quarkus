package org.apache.camel.quarkus.component.salesforce.codegen;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import io.quarkus.arc.impl.Sets;
import io.quarkus.bootstrap.prebuild.CodeGenException;
import io.quarkus.deployment.CodeGenContext;
import io.quarkus.deployment.CodeGenProvider;
import org.apache.camel.component.salesforce.codegen.GenerateExecution;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_CHILD_RELATIONSHIP_NAME_SUFFIX;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_CLIENT_ID;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_CLIENT_SECRET;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_CUSTOM_TYPES;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_ENUMERATION_OVERRIDE_PROPERTIES;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_EXCLUDES;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_EXCLUDE_PATTERN;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_CLIENT_PROPERTIES;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_AUTH_URI;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_DIGEST_AUTH;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_EXCLUDED_ADDRESSES;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_HOST;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_INCLUDED_ADDRESSES;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_PASSWORD;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_PORT;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_REALM;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_SECURE;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_SOCKS4;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_USERNAME;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_INCLUDES;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_INCLUDE_PATTERN;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_LOGIN_URL;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_LOGIN_URL_DEFAULT;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_OUTPUT_DIRECTORY;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_OUTPUT_DIRECTORY_DEFAULT;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_PACKAGE_NAME;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_PACKAGE_NAME_DEFAULT;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_PASSWORD;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_PREFIX;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_SKIP;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_USERNAME;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_USE_STRINGS_FOR_PICKLISTS;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_VERSION;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_VERSION_DEFAULT;

public class SalesforceCodegen implements CodeGenProvider {
    private static final Logger LOG = Logger.getLogger(SalesforceCodegen.class);

    @Override
    public String providerId() {
        return "camel-quarkus-salesforce";
    }

    @Override
    public String inputExtension() {
        // Fictional inputExtension required to satisfy CodeGenProvider requirements
        return "." + this.providerId();
    }

    @Override
    public String inputDirectory() {
        // Fictional inputDirectory required to satisfy CodeGenProvider requirements
        return this.providerId();
    }

    @Override
    public boolean trigger(CodeGenContext context) throws CodeGenException {
        GenerateExecution execution = new GenerateExecution();
        applyDTOGenerationConfig(context, execution);
        applyHttpProxyConfig(context, execution);
        try {
            execution.setup();
            execution.execute();
        } catch (Exception e) {
            throw new CodeGenException(e);
        }
        return true;
    }

    private void applyDTOGenerationConfig(CodeGenContext context, GenerateExecution execution) {
        Config config = context.config();
        getConfigValue(config, CONFIG_CLIENT_ID, String.class).ifPresent(execution::setClientId);
        getConfigValue(config, CONFIG_CLIENT_SECRET, String.class).ifPresent(execution::setClientSecret);
        getConfigValue(config, CONFIG_USERNAME, String.class).ifPresent(execution::setUserName);
        getConfigValue(config, CONFIG_PASSWORD, String.class).ifPresent(execution::setPassword);
        getConfigValue(config, CONFIG_LOGIN_URL, String.class).ifPresentOrElse(execution::setLoginUrl, () -> {
            execution.setLoginUrl(CONFIG_LOGIN_URL_DEFAULT);
        });
        getConfigValue(config, CONFIG_VERSION, String.class).ifPresentOrElse(execution::setVersion, () -> {
            execution.setVersion(CONFIG_VERSION_DEFAULT);
        });
        getConfigValue(config, CONFIG_OUTPUT_DIRECTORY, String.class)
                .map(File::new)
                .ifPresentOrElse(execution::setOutputDirectory, () -> {
                    File file = context.workDir()
                            .resolve(CONFIG_OUTPUT_DIRECTORY_DEFAULT).toFile();
                    execution.setOutputDirectory(file);
                });
        getConfigValue(config, CONFIG_INCLUDES, String[].class).ifPresent(execution::setIncludes);
        getConfigValue(config, CONFIG_EXCLUDES, String[].class).ifPresent(execution::setExcludes);
        getConfigValue(config, CONFIG_INCLUDE_PATTERN, String.class).ifPresent(execution::setIncludePattern);
        getConfigValue(config, CONFIG_EXCLUDE_PATTERN, String.class).ifPresent(execution::setExcludePattern);
        getConfigValue(config, CONFIG_PACKAGE_NAME, String.class).ifPresentOrElse(execution::setPackageName, () -> {
            execution.setPackageName(CONFIG_PACKAGE_NAME_DEFAULT);
        });
        getConfigValue(config, CONFIG_CUSTOM_TYPES, Map.class).ifPresent(execution::setCustomTypes);
        getConfigValue(config, CONFIG_USE_STRINGS_FOR_PICKLISTS, boolean.class).ifPresent(execution::setUseStringsForPicklists);
        getConfigValue(config, CONFIG_CHILD_RELATIONSHIP_NAME_SUFFIX, String.class)
                .ifPresent(execution::setChildRelationshipNameSuffix);
        getConfigValue(config, CONFIG_ENUMERATION_OVERRIDE_PROPERTIES, Properties.class)
                .ifPresent(execution::setEnumerationOverrideProperties);
        // TODO: Figure out why this is even a configuration option. How could it have been configured via maven properties?
        execution.setSslContextParameters(new SSLContextParameters());
        getConfigValue(config, CONFIG_HTTP_CLIENT_PROPERTIES, Map.class)
                .ifPresent(execution::setHttpClientProperties);
    }

    private void applyHttpProxyConfig(CodeGenContext context, GenerateExecution execution) {
        Config config = context.config();
        getConfigValue(config, CONFIG_HTTP_PROXY_HOST, String.class).ifPresent(execution::setHttpProxyHost);
        getConfigValue(config, CONFIG_HTTP_PROXY_PORT, Integer.class)
                .ifPresent(execution::setHttpProxyPort);
        getConfigValue(config, CONFIG_HTTP_PROXY_USERNAME, String.class)
                .ifPresent(execution::setHttpProxyUsername);
        getConfigValue(config, CONFIG_HTTP_PROXY_PASSWORD, String.class)
                .ifPresent(execution::setHttpProxyPassword);
        getConfigValue(config, CONFIG_HTTP_PROXY_REALM, String.class)
                .ifPresent(execution::setHttpProxyRealm);
        getConfigValue(config, CONFIG_HTTP_PROXY_AUTH_URI, String.class)
                .ifPresent(execution::setHttpProxyAuthUri);
        getConfigValue(config, CONFIG_HTTP_PROXY_DIGEST_AUTH, boolean.class)
                .ifPresent(execution::setHttpProxyUseDigestAuth);
        getConfigValue(config, CONFIG_HTTP_PROXY_INCLUDED_ADDRESSES, String[].class)
                .map(Sets::of)
                .ifPresent(execution::setHttpProxyIncludedAddresses);
        getConfigValue(config, CONFIG_HTTP_PROXY_EXCLUDED_ADDRESSES, String[].class)
                .map(Sets::of)
                .ifPresent(execution::setHttpProxyExcludedAddresses);
        getConfigValue(config, CONFIG_HTTP_PROXY_SECURE, boolean.class)
                .ifPresent(execution::setHttpProxySecure);
        getConfigValue(config, CONFIG_HTTP_PROXY_SOCKS4, boolean.class)
                .ifPresent(execution::setHttpProxySocks4);
    }

    @Override
    public boolean shouldRun(Path sourceDir, Config config) {
        if (LOG.isDebugEnabled()) {
            Iterable<String> propertyNames = config.getPropertyNames();
            propertyNames.forEach(propertyName -> {
                if (propertyName.startsWith(CONFIG_PREFIX)) {
                    LOG.debugf("Found Camel Quarkus Salesforce CodeGen property: %s", propertyName);
                }
            });
        }

        if (!shouldRunInternal(config)) {
            LOG.info("Skipping Salesforce code generation");
            return false;
        }
        return true;
    }

    private boolean shouldRunInternal(Config config) {
        Optional<Boolean> skipCodeGenOptional = config.getOptionalValue(resolveConfigKey(CONFIG_SKIP), Boolean.class);
        return skipCodeGenOptional.map(skip -> !skip)
                .orElseGet(() -> isSalesforceCredentialsPresent(config));
    }

    private boolean isSalesforceCredentialsPresent(Config config) {
        return getConfigValue(config, CONFIG_CLIENT_ID, String.class).isPresent() &&
                getConfigValue(config, CONFIG_CLIENT_SECRET, String.class).isPresent() &&
                getConfigValue(config, CONFIG_USERNAME, String.class).isPresent() &&
                getConfigValue(config, CONFIG_PASSWORD, String.class).isPresent();
    }

    private <T> Optional<T> getConfigValue(Config config, String key, Class<T> aClass) {
        String resolvedKey = resolveConfigKey(key);
        if (Properties.class.isAssignableFrom(aClass)) {
            final Properties properties = new Properties();
            populateMapConfigType(config, key, properties);
            return (Optional<T>) Optional.of(properties);
        } else if (Map.class.isAssignableFrom(aClass)) {
            final Map<Object, Object> map = new HashMap<>();
            populateMapConfigType(config, key, map);
            return (Optional<T>) Optional.of(map);
        }

        return config.getOptionalValue(resolvedKey, aClass);
    }

    // TODO: Rethink how this works...
    // Can probably use map style config or list of key=value types
    private void populateMapConfigType(Config config, String key, Map<Object, Object> map) {
        String resolvedKey = resolveConfigKey(key);
        config.getPropertyNames().forEach(propertyName -> {
            if (propertyName.startsWith(resolvedKey)) {
                String suffix = propertyName.replace(key + ".", "");
                map.put(suffix, config.getValue(propertyName, String.class));
            }
        });
    }

    private String resolveConfigKey(String key) {
        return String.format("%s.%s", CONFIG_PREFIX, key);
    }
}
