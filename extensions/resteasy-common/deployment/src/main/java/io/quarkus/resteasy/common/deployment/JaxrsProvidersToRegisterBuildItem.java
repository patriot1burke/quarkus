package io.quarkus.resteasy.common.deployment;

import java.util.HashSet;
import java.util.Set;

import io.quarkus.builder.item.SimpleBuildItem;

public final class JaxrsProvidersToRegisterBuildItem extends SimpleBuildItem {

    private final Set<String> builtin;
    private final Set<String> providers;
    private final Set<String> contributedProviders;

    public JaxrsProvidersToRegisterBuildItem() {
        builtin = new HashSet<>();
        providers = new HashSet<>();
        contributedProviders = new HashSet<>();
    }

    public JaxrsProvidersToRegisterBuildItem(Set<String> builtin, Set<String> providers, Set<String> contributedProviders) {
        this.builtin = builtin;
        this.providers = providers;
        this.contributedProviders = contributedProviders;
    }

    public Set<String> getBuiltin() {
        return builtin;
    }

    public Set<String> getProviders() {
        return this.providers;
    }

    public Set<String> getContributedProviders() {
        return this.contributedProviders;
    }
}
