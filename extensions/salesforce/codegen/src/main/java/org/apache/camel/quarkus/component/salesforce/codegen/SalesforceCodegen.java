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
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_AUTH_URI;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_DIGEST_AUTH;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_EXCLUDED_ADDRESSES;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_HOST;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_INCLUDED_ADDRESSES;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_PASSWORD;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_PORT;
import static org.apache.camel.quarkus.salesforce.config.SalesforceCodegenConfig.CONFIG_HTTP_PROXY_REALM;
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
        return ".camel";
    }

    @Override
    public String inputDirectory() {
        return "null";
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
        execution.setSslContextParameters(new SSLContextParameters());
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
    }

    @Override
    public boolean shouldRun(Path sourceDir, Config config) {
        config.getPropertyNames().forEach(System.out::println);

        if (!shouldRunInternal(config)) {
            LOG.info("Skipping Salesforce code generation");
            return false;
        }
        return true;
    }

    private boolean shouldRunInternal(Config config) {
        String skipConfigKey = resolveConfigKey(CONFIG_SKIP);
        Optional<Boolean> optional = config.getOptionalValue(skipConfigKey, boolean.class);
        if (optional.isPresent()) {
            LOG.info("Uh oh! Skip config present...");
            return !optional.get();
        }

        if (Boolean.getBoolean(skipConfigKey)) {
            LOG.info("Uh oh! Skip config present...");
            return false;
        }

        // TODO: Do we need to be looking up system properties as well?
        //        String clientIdConfigKey = getConfigValue(config, CONFIG_CLIENT_ID, String.class).orElse(null);
        //        String clientSecretConfigKey = getConfigValue(config, CONFIG_CLIENT_SECRET, String.class).orElse(null);
        //        if (ObjectHelper.isEmpty(clientIdConfigKey) || ObjectHelper.isEmpty(clientSecretConfigKey)) {
        //            LOG.info("Uh oh! Client id / secret not present...");
        //            return false;
        //        }

        return true;
    }

    private <T extends Object> Optional<T> getConfigValue(Config config, String key, Class<T> aClass) {
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

    private void populateMapConfigType(Config config, String key, Map<Object, Object> map) {
        String resolvedKey = resolveConfigKey(key);
        config.getPropertyNames().forEach(propertyName -> {
            if (propertyName.startsWith(resolvedKey)) {
                // TODO: This can't work in all cases. The '.' in the suffix might be important like Student__c.FinalGrade__c.A-
                String suffix = propertyName.substring(propertyName.lastIndexOf('.') + 1);
                map.put(suffix, config.getValue(propertyName, String.class));
            }
        });
    }

    // TODO: This is actually really tricky...
    // How do you get
    private <T extends Object> Optional<T> resolveConfigValue(Config config, String key, Class<T> aClass) {
        String resolvedKey = resolveConfigKey(key);
        // TODO: Handle collections
        String property = System.getProperty(resolvedKey);
        if (property != null) {
            return Optional.of((T) property);
        }
        return null;
    }

    private String resolveConfigKey(String key) {
        return String.format("%s.%s", CONFIG_PREFIX, key);
    }
}
