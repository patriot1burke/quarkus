package io.quarkus.it.azure.functions;

import javax.inject.Inject;

import com.microsoft.azure.functions.annotation.FunctionName;

public class MyFunction {
    @Inject
    GreetingService service;

    @FunctionName("simple")
    public String hello() {
        return service.getGreeting();
    }
}
