package org.apache.camel.quarkus.salesforce.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;

/**
 * Not used by the Salesforce extension. Exists only to document codegen configuration and cut down on code duplication.
 */
@ConfigRoot(name = "camel.salesforce.codegen", phase = ConfigPhase.BUILD_TIME)
public class SalesforceCodegenConfig {
    public static final String CONFIG_PREFIX = "quarkus.camel.salesforce.codegen";
    public static final String CONFIG_SKIP = "skip";
    public static final String CONFIG_CLIENT_ID = "client-id";
    public static final String CONFIG_CLIENT_SECRET = "client-secret";
    public static final String CONFIG_USERNAME = "username";
    public static final String CONFIG_PASSWORD = "password";
    public static final String CONFIG_LOGIN_URL = "login-url";
    public static final String CONFIG_LOGIN_URL_DEFAULT = SalesforceLoginConfig.DEFAULT_LOGIN_URL;
    public static final String CONFIG_VERSION = "version";
    public static final String CONFIG_VERSION_DEFAULT = SalesforceEndpointConfig.DEFAULT_VERSION;
    public static final String CONFIG_OUTPUT_DIRECTORY = "output-directory";
    public static final String CONFIG_OUTPUT_DIRECTORY_DEFAULT = "generated-sources/camel-salesforce";
    public static final String CONFIG_INCLUDES = "includes";
    public static final String CONFIG_EXCLUDES = "excludes";
    public static final String CONFIG_INCLUDE_PATTERN = "include-pattern";
    public static final String CONFIG_EXCLUDE_PATTERN = "exclude-pattern";
    public static final String CONFIG_PACKAGE_NAME = "package-name";
    public static final String CONFIG_PACKAGE_NAME_DEFAULT = "org.apache.camel.salesforce.dto";
    public static final String CONFIG_CUSTOM_TYPES = "custom-types";
    public static final String CONFIG_USE_STRINGS_FOR_PICKLISTS = "use-strings-for-picklists";
    public static final String CONFIG_CHILD_RELATIONSHIP_NAME_SUFFIX = "child-relationship-name-suffix";
    public static final String CONFIG_ENUMERATION_OVERRIDE_PROPERTIES = "enumeration-override-properties";
    public static final String CONFIG_HTTP_CLIENT_PROPERTIES = "http-client-properties";
    public static final String CONFIG_HTTP_PROXY = "http.proxy.";
    public static final String CONFIG_HTTP_PROXY_HOST = CONFIG_HTTP_PROXY + "host";
    public static final String CONFIG_HTTP_PROXY_PORT = CONFIG_HTTP_PROXY + "port";
    public static final String CONFIG_HTTP_PROXY_USERNAME = CONFIG_HTTP_PROXY + "username";
    public static final String CONFIG_HTTP_PROXY_PASSWORD = CONFIG_HTTP_PROXY + "password";
    public static final String CONFIG_HTTP_PROXY_REALM = CONFIG_HTTP_PROXY + "realm";
    public static final String CONFIG_HTTP_PROXY_AUTH_URI = CONFIG_HTTP_PROXY + "auth-uri";
    public static final String CONFIG_HTTP_PROXY_DIGEST_AUTH = CONFIG_HTTP_PROXY + "use-digest-auth";
    public static final String CONFIG_HTTP_PROXY_INCLUDED_ADDRESSES = CONFIG_HTTP_PROXY + "included-addresses";
    public static final String CONFIG_HTTP_PROXY_EXCLUDED_ADDRESSES = CONFIG_HTTP_PROXY + "excluded-addresses";
    public static final String CONFIG_HTTP_PROXY_SECURE = CONFIG_HTTP_PROXY + "secure";
    public static final String CONFIG_HTTP_PROXY_SOCKS4 = CONFIG_HTTP_PROXY + "socks4";

    /**
     * Skips Salesforce DTO code generation.
     */
    @ConfigItem(name = CONFIG_SKIP, defaultValue = "false")
    public Optional<Boolean> skip;

    /**
     * Salesforce API client ID.
     */
    @ConfigItem(name = CONFIG_CLIENT_ID)
    public Optional<String> clientId;

    /**
     * Salesforce API client secret.
     */
    @ConfigItem(name = CONFIG_CLIENT_SECRET)
    public Optional<String> clientSecret;

    /**
     * Salesforce API username.
     */
    @ConfigItem(name = CONFIG_USERNAME)
    public Optional<String> userName;

    /**
     * Salesforce API password.
     */
    @ConfigItem(name = CONFIG_PASSWORD)
    public Optional<String> password;

    /**
     * Salesforce API login URL.
     */
    @ConfigItem(name = CONFIG_LOGIN_URL, defaultValue = CONFIG_LOGIN_URL_DEFAULT)
    public Optional<String> loginUrl;

    /**
     * Salesforce API version.
     */
    @ConfigItem(name = CONFIG_VERSION, defaultValue = CONFIG_VERSION_DEFAULT)
    public Optional<String> version;

    /**
     * Directory where to place generated DTOs, defaults to
     * <project-build-output-directory>/generated-sources/camel-salesforce.
     */
    @ConfigItem(name = CONFIG_OUTPUT_DIRECTORY)
    public Optional<String> outputDirectory;

    /**
     * List of SObject types to include.
     */
    @ConfigItem(name = CONFIG_INCLUDES)
    public Optional<String[]> includes;

    /**
     * List of SObject types to exclude.
     */
    @ConfigItem(name = CONFIG_EXCLUDES)
    public Optional<String[]> excludes;

    /**
     * Java RegEx for SObject types to include.
     */
    @ConfigItem(name = CONFIG_INCLUDE_PATTERN)
    public Optional<String> includePattern;

    /**
     * Java RegEx for SObject types to exclude.
     */
    @ConfigItem(name = CONFIG_EXCLUDE_PATTERN)
    public Optional<String> excludePattern;

    /**
     * Java package name for generated DTOs.
     */
    @ConfigItem(name = CONFIG_PACKAGE_NAME, defaultValue = CONFIG_PACKAGE_NAME_DEFAULT)
    public Optional<String> packageName;

    /**
     * Override default types in generated DTOs.
     */
    @ConfigItem(name = CONFIG_CUSTOM_TYPES)
    public Optional<Map<String, String>> customTypes;

    /**
     * Use strings instead of enumerations for picklists.
     */
    @ConfigItem(name = CONFIG_USE_STRINGS_FOR_PICKLISTS, defaultValue = "false")
    public boolean useStringForPicklists;

    /**
     * Suffix for child relationship property name. Necessary if an SObject has a lookup field with the same name
     * as its Child Relationship Name. If setting to something other than default, "List" is a sensible value.
     */
    @ConfigItem(name = CONFIG_CHILD_RELATIONSHIP_NAME_SUFFIX)
    public Optional<String> childRelationshipNameSuffix;

    /**
     * Override picklist enum value generation via a java.util.Properties instance.
     */
    @ConfigItem(name = CONFIG_ENUMERATION_OVERRIDE_PROPERTIES)
    public Optional<String> enumerationOverrideProperties;

    /**
     * Override configuration properties related to the underlying Apache Commons HTTP Client.
     */
    @ConfigItem(name = CONFIG_HTTP_CLIENT_PROPERTIES)
    public Optional<Map<String, String>> httpClientProperties;

    /**
     * HTTP proxy server configuration for the Salesforce HTTP client
     */
    @ConfigItem(name = ConfigItem.PARENT)
    public HttpProxyConfiguration httpProxy;

    @ConfigGroup
    public static class HttpProxyConfiguration {
        /**
         * Proxy server host name
         */
        @ConfigItem(name = CONFIG_HTTP_PROXY_HOST)
        public Optional<String> host;

        /**
         * Proxy server host port
         */
        @ConfigItem(name = CONFIG_HTTP_PROXY_PORT)
        public OptionalInt port;

        /**
         * Proxy server username
         */
        @ConfigItem(name = CONFIG_HTTP_PROXY_USERNAME)
        public Optional<String> username;

        /**
         * Proxy server password
         */
        @ConfigItem(name = CONFIG_HTTP_PROXY_PASSWORD)
        public Optional<String> password;

        /**
         * Proxy server realm
         */
        @ConfigItem(name = CONFIG_HTTP_PROXY_REALM)
        public Optional<String> realm;

        /**
         * Proxy server authentication URI
         */
        @ConfigItem(name = CONFIG_HTTP_PROXY_AUTH_URI)
        public Optional<String> authUri;

        /**
         * Whether to use proxy digest authentication
         */
        @ConfigItem(name = CONFIG_HTTP_PROXY_DIGEST_AUTH, defaultValue = "false")
        public Optional<Boolean> useDigestAuth;

        /**
         * Addresses to include for the proxy server
         */
        @ConfigItem(name = CONFIG_HTTP_PROXY_INCLUDED_ADDRESSES)
        public Optional<String[]> includedAddresses;

        /**
         * Addresses to exclude for the proxy server
         */
        @ConfigItem(name = CONFIG_HTTP_PROXY_EXCLUDED_ADDRESSES)
        public Optional<String[]> excludedAddresses;

        /**
         * Whether the proxy server is secured by SSL / TLS
         */
        @ConfigItem(name = CONFIG_HTTP_PROXY_SECURE, defaultValue = "true")
        public Optional<Boolean[]> secure;

        /**
         * Whether the proxy server is secured by SOCKS4
         */
        @ConfigItem(name = CONFIG_HTTP_PROXY_SOCKS4, defaultValue = "false")
        public Optional<Boolean[]> socks4;
    }
}
