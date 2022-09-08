package io.quarkus.azure.functions.runtime;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.middleware.FunctionWorkerChain;
import com.microsoft.azure.functions.middleware.FunctionWorkerMiddleware;

public class QuarkusFunctionMiddleware implements FunctionWorkerMiddleware {
    @Override
    public void invoke(ExecutionContext context, FunctionWorkerChain next) throws Exception {
        Object functionInstance = QuarkusFunctionFactory.newInstance(context.getContainingClass());
        context.setFunctionInstance(functionInstance);
        next.doNext(context);
    }
}
