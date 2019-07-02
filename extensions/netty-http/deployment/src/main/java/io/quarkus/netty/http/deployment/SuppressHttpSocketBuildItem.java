package io.quarkus.netty.http.deployment;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Marker class to turn off http socket creation
 */
public final class SuppressHttpSocketBuildItem extends SimpleBuildItem {
    public static final SuppressHttpSocketBuildItem MARKER = new SuppressHttpSocketBuildItem();
}
