package io.quarkus.resteasy.runtime.standalone;

import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class ResteasyStandaloneConfiguration {
    /**
     * Buffer size
     */
    @ConfigItem
    public OptionalInt bufferSize;

    /**
     * write queue size
     */
    @ConfigItem
    public OptionalInt writeQueueSize;

    /**
     * non blocking
     */
    @ConfigItem(defaultValue = "false")
    public boolean nonBlockingRequests;
}
