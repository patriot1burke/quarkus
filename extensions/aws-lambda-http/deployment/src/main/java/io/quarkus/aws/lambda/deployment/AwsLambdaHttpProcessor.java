package io.quarkus.aws.lambda.deployment;

import org.jboss.logging.Logger;

import io.quarkus.amazon.lambda.deployment.ProvidedAmazonLambdaHandlerBuildItem;
import io.quarkus.aws.lambda.runtime.QuarkusAwsLambdaHttpHandler;
import io.quarkus.aws.lambda.runtime.model.AlbContext;
import io.quarkus.aws.lambda.runtime.model.ApiGatewayAuthorizerContext;
import io.quarkus.aws.lambda.runtime.model.ApiGatewayRequestIdentity;
import io.quarkus.aws.lambda.runtime.model.AwsProxyRequest;
import io.quarkus.aws.lambda.runtime.model.AwsProxyRequestContext;
import io.quarkus.aws.lambda.runtime.model.AwsProxyResponse;
import io.quarkus.aws.lambda.runtime.model.CognitoAuthorizerClaims;
import io.quarkus.aws.lambda.runtime.model.ErrorModel;
import io.quarkus.aws.lambda.runtime.model.Headers;
import io.quarkus.aws.lambda.runtime.model.MultiValuedTreeMap;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.substrate.ReflectiveClassBuildItem;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.vertx.http.deployment.RequireVirtualHttpBuildItem;

public class AwsLambdaHttpProcessor {
    private static final Logger log = Logger.getLogger(AwsLambdaHttpProcessor.class);

    @BuildStep
    public RequireVirtualHttpBuildItem requestVirtualHttp(LaunchModeBuildItem launchMode) {
        return launchMode.getLaunchMode() == LaunchMode.NORMAL ? RequireVirtualHttpBuildItem.MARKER : null;
    }

    @BuildStep
    public ProvidedAmazonLambdaHandlerBuildItem setHandler() {
        return new ProvidedAmazonLambdaHandlerBuildItem(QuarkusAwsLambdaHttpHandler.class, "AWS Lambda HTTP");
    }

    @BuildStep
    public void registerReflectionClasses(BuildProducer<ReflectiveClassBuildItem> reflectiveClassBuildItemBuildProducer) {
        reflectiveClassBuildItemBuildProducer
                .produce(new ReflectiveClassBuildItem(true, true, true,
                        AlbContext.class,
                        ApiGatewayAuthorizerContext.class,
                        ApiGatewayRequestIdentity.class,
                        AwsProxyRequest.class,
                        AwsProxyRequestContext.class,
                        AwsProxyResponse.class,
                        CognitoAuthorizerClaims.class,
                        ErrorModel.class,
                        Headers.class,
                        MultiValuedTreeMap.class));
    }
}
