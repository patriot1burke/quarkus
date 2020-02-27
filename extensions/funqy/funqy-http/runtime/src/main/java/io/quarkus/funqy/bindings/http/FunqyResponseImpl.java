package io.quarkus.funqy.bindings.http;

import io.quarkus.funqy.runtime.FunqyServerResponse;

public class FunqyResponseImpl implements FunqyServerResponse {
    protected Object output;

    public Object getOutput() {
        return output;
    }

    @Override
    public void setOutput(Object output) {
        this.output = output;
    }
}
