package io.quarkus.restclient.runtime;

import java.util.Set;

import javax.ws.rs.RuntimeType;

import org.eclipse.microprofile.rest.client.spi.RestClientBuilderResolver;
import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;
import org.jboss.resteasy.microprofile.client.RestClientBuilderImpl;
import org.jboss.resteasy.spi.InjectorFactory;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import io.quarkus.resteasy.common.runtime.ResteasyRegistrationRecorder;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RestClientRecorder {
    public void setRestClientBuilderResolver() {
        RestClientBuilderResolver.setInstance(new BuilderResolver());
    }

    public void setSslEnabled(boolean sslEnabled) {
        RestClientBuilderImpl.setSslEnabled(sslEnabled);
    }

    public void initializeFactory(RuntimeValue<InjectorFactory> factory) {
        ResteasyRegistrationRecorder.providerFactory = new ResteasyProviderFactoryImpl(RuntimeType.CLIENT) {
            @Override
            public RuntimeType getRuntimeType() {
                return RuntimeType.CLIENT;
            }

            @Override
            public InjectorFactory getInjectorFactory() {
                return factory.getValue();
            }
        };
    }

    public void bindFactory() {
        ResteasyProviderFactoryImpl clientProviderFactory = ResteasyRegistrationRecorder.providerFactory;
        RestClientBuilderImpl.setProviderFactory(clientProviderFactory);
        ResteasyRegistrationRecorder.providerFactory = null;
    }

    private static void registerProviders(ResteasyProviderFactory clientProviderFactory, Set<String> providersToRegister,
            Boolean isBuiltIn) {
        for (String providerToRegister : providersToRegister) {
            try {
                clientProviderFactory
                        .registerProvider(Thread.currentThread().getContextClassLoader().loadClass(providerToRegister.trim()),
                                isBuiltIn);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Unable to find class for provider " + providerToRegister, e);
            }
        }
    }
}
