package io.quarkus.azure.functions.runtime;

import com.microsoft.azure.functions.middleware.FunctionWorkerChain;
import com.microsoft.azure.functions.middleware.FunctionWorkerMiddleware;
import com.microsoft.azure.functions.middleware.MiddlewareExecutionContext;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.runtime.Quarkus;

public class QuarkusAzureFunctionsMiddleware implements FunctionWorkerMiddleware {
    public QuarkusAzureFunctionsMiddleware() {
        try {
            Quarkus.bootstrap();
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public void invoke(MiddlewareExecutionContext context, FunctionWorkerChain next) throws Exception {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            Object obj = Arc.container().instance(context.getFunctionClass());
            context.setFunctionInstance(obj);
            next.doNext(context);
        } finally {
            if (requestContext.isActive()) {
                requestContext.terminate();
            }
        }
    }
}
