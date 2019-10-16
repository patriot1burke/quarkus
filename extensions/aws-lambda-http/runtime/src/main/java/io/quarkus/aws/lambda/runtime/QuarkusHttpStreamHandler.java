package io.quarkus.aws.lambda.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.aws.lambda.runtime.model.AwsProxyRequest;
import io.quarkus.aws.lambda.runtime.model.AwsProxyResponse;
import io.quarkus.runtime.Application;

public class QuarkusHttpStreamHandler implements RequestStreamHandler {
    private static ObjectMapper objectMapper = new ObjectMapper();

    protected static final String deploymentStatus;
    protected static boolean started = false;

    static {
        StringWriter error = new StringWriter();
        PrintWriter errorWriter = new PrintWriter(error, true);
        if (Application.currentApplication() == null) { // were we already bootstrapped?  Needed for mock azure unit testing.
            try {
                Class appClass = Class.forName("io.quarkus.runner.ApplicationImpl1");
                String[] args = {};
                Application app = (Application) appClass.newInstance();
                app.start(args);
                errorWriter.println("Quarkus bootstrapped successfully.");
                started = true;
            } catch (Exception ex) {
                errorWriter.println("Quarkus bootstrap failed.");
                ex.printStackTrace(errorWriter);
            }
        } else {
            errorWriter.println("Quarkus bootstrapped successfully.");
            started = true;
        }
        deploymentStatus = error.toString();
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        AwsProxyRequest request = objectMapper.readerFor(AwsProxyRequest.class).readValue(inputStream);
        AwsProxyResponse response = QuarkusAwsLambdaHttpHandler.handle(request);
        objectMapper.writerFor(AwsProxyResponse.class).writeValue(outputStream, response);
    }
}
