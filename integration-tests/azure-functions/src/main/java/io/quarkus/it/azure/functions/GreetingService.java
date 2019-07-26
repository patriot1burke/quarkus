package io.quarkus.it.azure.functions;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GreetingService {
    protected String greeting = "hello world";

    public String getGreeting() {
        return greeting;
    }

}
