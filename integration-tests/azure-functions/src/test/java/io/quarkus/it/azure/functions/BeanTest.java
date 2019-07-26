package io.quarkus.it.azure.functions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class BeanTest {

    protected <T> T createFunction(Class<T> functionClass) throws Exception {
        Method method = getFunctionFactory();
        return (T) method.invoke(null, functionClass);
    }

    private Method getFunctionFactory() throws IOException, ClassNotFoundException, NoSuchMethodException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        InputStream is = cl.getResourceAsStream("META-INF/service/com.microsoft.azure.functions.FunctionFactory");
        if (is == null)
            throw new RuntimeException("NONE FOUND");
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String factoryName = reader.readLine().trim();
        Class factoryClass = cl.loadClass(factoryName);
        return factoryClass.getMethod("newInstance", Class.class);
    }

    @Test
    public void functionNameTest() throws Exception {
        MyFunction myFunction = createFunction(MyFunction.class);
        Assertions.assertNotNull(myFunction);
        Assertions.assertEquals("hello world", myFunction.hello());

        Method m = MyFunction.class.getMethod("hello");
        String greeting = (String) m.invoke(myFunction);
    }
}
