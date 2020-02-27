package io.quarkus.funqy.bindings.http;

import io.quarkus.funqy.runtime.FunqyServerRequest;
import io.quarkus.funqy.runtime.RequestContext;

public class FunqyRequestImpl implements FunqyServerRequest {
    protected RequestContext requestContext;
    protected Object input;

    public FunqyRequestImpl(RequestContext requestContext, Object input) {
        this.requestContext = requestContext;
        this.input = input;
    }

    @Override
    public RequestContext context() {
        return null;
    }

    @Override
    public Object extractInput(Class inputClass) {
        return input;
    }
}
