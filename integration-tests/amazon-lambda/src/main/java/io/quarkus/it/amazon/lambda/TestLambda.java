package io.quarkus.it.amazon.lambda;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

@ApplicationScoped
@Named("test")
public class TestLambda implements RequestHandler<InputObject, OutputObject> {

    @Inject
    ProcessingService service;

    @ConfigProperty(name = "prop.name")
    String propName;

    @Count
    @Override
    public OutputObject handleRequest(InputObject input, Context context) {
        if (propName == null || !propName.equals("hello"))
            throw new RuntimeException("config property not set");
        return service.proces(input).setRequestId(context.getAwsRequestId());
    }
}
