package io.quarkus.devspace.operator;

import java.util.HashMap;
import java.util.Map;

public class DevspaceProxyStatus {
    private boolean initialized;
    private Map<String, String> oldSelectors = new HashMap<>();

    public Map<String, String> getOldSelectors() {
        return oldSelectors;
    }

    public void setOldSelectors(Map<String, String> oldSelectors) {
        this.oldSelectors = oldSelectors;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }
}