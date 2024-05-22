package io.quarkus.vertx.http.runtime;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.quarkus.runtime.annotations.ConvertWith;
import io.quarkus.runtime.configuration.NormalizeRootHttpPathConverter;
import io.quarkus.vertx.http.Compressed;
import io.quarkus.vertx.http.Uncompressed;
import io.vertx.core.http.ClientAuth;

@ConfigRoot(name = "http", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class HttpBuildTimeConfig {

    /**
     * The HTTP root path. All web content will be served relative to this root path.
     */
    @ConfigItem(defaultValue = "/")
    @ConvertWith(NormalizeRootHttpPathConverter.class)
    public String rootPath;

    public AuthConfig auth;

    /**
     * Configures the engine to require/request client authentication.
     * {@code NONE, REQUEST, REQUIRED}.
     * <p>
     * When set to {@code REQUIRED}, it's recommended to also set `quarkus.http.insecure-requests=disabled` to disable the
     * plain HTTP port. If `quarkus.http.insecure-requests` is not set, but this parameter is set to {@code REQUIRED}, then,
     * `quarkus.http.insecure-requests` is automatically set to `disabled`.
     */
    @ConfigItem(name = "ssl.client-auth", defaultValue = "NONE")
    public ClientAuth tlsClientAuth;

    /**
     * If this is true then only a virtual channel will be set up for vertx web.
     * We have this switch for testing purposes.
     */
    @ConfigItem
    public boolean virtual;

    /**
     * A common root path for non-application endpoints. Various extension-provided endpoints such as metrics, health,
     * and openapi are deployed under this path by default.
     * <p>
     * * Relative path (Default, `q`) ->
     * Non-application endpoints will be served from
     * `${quarkus.http.root-path}/${quarkus.http.non-application-root-path}`.
     * * Absolute path (`/q`) ->
     * Non-application endpoints will be served from the specified path.
     * * `${quarkus.http.root-path}` -> Setting this path to the same value as HTTP root path disables
     * this root path. All extension-provided endpoints will be served from `${quarkus.http.root-path}`.
     * <p>
     * If the management interface is enabled, the root path for the endpoints exposed on the management interface
     * is configured using the `quarkus.management.root-path` property instead of this property.
     *
     * @asciidoclet
     */
    @ConfigItem(defaultValue = "q")
    public String nonApplicationRootPath;

    /**
     * The REST Assured client timeout for testing.
     */
    @ConfigItem(defaultValue = "30s")
    public Duration testTimeout;

    /**
     * If enabled then the response body is compressed if the {@code Content-Type} header is set and the value is a compressed
     * media type as configured via {@link #compressMediaTypes}.
     * <p>
     * Note that the RESTEasy Reactive and Reactive Routes extensions also make it possible to enable/disable compression
     * declaratively using the annotations {@link io.quarkus.vertx.http.Compressed} and
     * {@link io.quarkus.vertx.http.Uncompressed}.
     */
    @ConfigItem
    public boolean enableCompression;

    /**
     * When enabled, vert.x will decompress the request's body if it's compressed.
     * <p>
     * Note that the compression format (e.g., gzip) must be specified in the Content-Encoding header
     * in the request.
     */
    @ConfigItem
    public boolean enableDecompression;

    /**
     * List of media types for which the compression should be enabled automatically, unless declared explicitly via
     * {@link Compressed} or {@link Uncompressed}.
     */
    @ConfigItem(defaultValue = "text/html,text/plain,text/xml,text/css,text/javascript,application/javascript,application/graphql+json")
    public Optional<List<String>> compressMediaTypes;

    /**
     * The compression level used when compression support is enabled.
     */
    @ConfigItem
    public OptionalInt compressionLevel;

    /**
     * Connection string for quarkus devspace.
     *
     * Uri. Add query parameters to uri for additional config parameters
     *
     * i.e.
     * http://host:port?who=whoami[&optional-config=value]*
     *
     * "who" is who you are. This is required.
     *
     * By default, all request will be pushed locally from the devspace proxy.
     * If you want to have a specific directed session, then use these parameters to define
     * the session within the devspace config uri:
     *
     * header - http header or cookie name that identifies the session id
     * query - query parameter name that identifies session id
     * path - path parameter name that identifies session id use "{}" to specify where sessionid is in path i.e.
     * /foo/bar/{}/blah
     * session - session id value
     */
    @ConfigItem
    public Optional<String> devspace;

    /**
     * If true, quarkus will not connect to devspace on boot. Connection would have
     * to be done manually from the recorder method.
     *
     * This is for internal testing purposes only.
     */
    @ConfigItem(defaultValue = "false")
    public boolean devspaceDelayConnect;
}
