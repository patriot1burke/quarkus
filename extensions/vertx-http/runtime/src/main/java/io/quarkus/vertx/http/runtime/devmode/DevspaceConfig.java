package io.quarkus.vertx.http.runtime.devmode;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

/**
 * Devspace config
 */
@ConfigGroup
public class DevspaceConfig {
    /**
     *
     */
    @ConfigItem
    public Optional<String> uri;

    /**
     *
     */
    @ConfigItem
    public Optional<String> session;
    /**
     *
     */
    @ConfigItem
    public Optional<String> whoami;
    /**
     * This is for internal testing purposes
     */
    @ConfigItem(defaultValue = "false")
    public boolean delayConnect;
}
