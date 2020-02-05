package io.quarkus.resteasy.runtime.standalone;

import java.util.TreeMap;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.core.ThreadLocalResteasyProviderFactory;
import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;
import org.jboss.resteasy.plugins.interceptors.RoleBasedSecurityFeature;

public class QuarkusResteasyDeployment extends ResteasyDeploymentImpl {
    public QuarkusResteasyDeployment() {
        super(false);
        properties = new TreeMap<String, Object>();
    }

    protected boolean useScanning;

    public void initialize() {
        try {
            initializeFactory();
            initializeDispatcher();
            pushContext();
            initializeObjects();
            useScanning = registerApplication();
        } catch (RuntimeException e) {
            ThreadLocalResteasyProviderFactory.pop();
            ResteasyContext.removeContextDataLevel();
            throw e;
        }
    }

    @Override
    public void start() {
        try {
            if (securityEnabled) {
                providerFactory.register(RoleBasedSecurityFeature.class);
            }
            registerResources(useScanning);
            registerMappers();
            ((ResteasyProviderFactoryImpl) providerFactory).lockSnapshots();
        } finally {
            ThreadLocalResteasyProviderFactory.pop();
            ResteasyContext.removeContextDataLevel();
        }
    }
}
