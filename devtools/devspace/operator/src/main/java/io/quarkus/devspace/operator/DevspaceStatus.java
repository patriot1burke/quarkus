package io.quarkus.devspace.operator;

import java.util.HashMap;
import java.util.Map;

public class DevspaceStatus {
    public enum State {
        CREATED,
        ENABLED,
        DISABLED
    }
    private State state = State.CREATED;
    private Map<String, String> oldSelectors = new HashMap<>();

    public Map<String, String> getOldSelectors() {
        return oldSelectors;
    }

    public void setOldSelectors(Map<String, String> oldSelectors) {
        this.oldSelectors = oldSelectors;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }
}