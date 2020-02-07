package io.quarkus.resteasy.runtime;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.core.ThreadLocalResteasyProviderFactory;
import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;
import org.jboss.resteasy.plugins.interceptors.RoleBasedSecurityFeature;

public class QuarkusResteasyDeployment extends ResteasyDeploymentImpl {
    protected List<String> builtinProviderClasses;

    public QuarkusResteasyDeployment() {
        //super(false);
        //properties = new TreeMap<String, Object>();
        defaultContextObjects = new HashMap<Class, Object>();
        builtinProviderClasses = new LinkedList<>();
    }

    public List<String> getBuiltinProviderClasses() {
        return builtinProviderClasses;
    }

    public void setBuiltinProviderClasses(List<String> builtinProviderClasses) {
        this.builtinProviderClasses = builtinProviderClasses;
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

    public void prune() {
        builtinProviderClasses = null;
        if (scannedResourceClasses.isEmpty())
            scannedResourceClasses = null;
        if (scannedProviderClasses.isEmpty())
            scannedProviderClasses = null;
        if (scannedJndiComponentResources.isEmpty())
            scannedJndiComponentResources = null;
        if (scannedResourceClassesWithBuilder.isEmpty())
            scannedResourceClassesWithBuilder = null;
        if (jndiComponentResources.isEmpty())
            jndiComponentResources = null;
        if (providerClasses.isEmpty())
            providerClasses = null;
        if (actualProviderClasses.isEmpty())
            actualProviderClasses = null;
        if (providers.isEmpty())
            providers = null;
        if (jndiResources.isEmpty())
            jndiResources = null;
        if (resourceClasses.isEmpty())
            resourceClasses = null;
        if (unwrappedExceptions.isEmpty())
            unwrappedExceptions = null;
        if (actualResourceClasses.isEmpty())
            actualResourceClasses = null;
        if (resourceFactories.isEmpty())
            resourceFactories = null;
        if (resources.isEmpty())
            resources = null;
        if (mediaTypeMappings.isEmpty())
            mediaTypeMappings = null;
        if (languageExtensions.isEmpty())
            languageExtensions = null;
        if (defaultContextObjects.isEmpty())
            defaultContextObjects = null;
        if (constructedDefaultContextObjects.isEmpty())
            constructedDefaultContextObjects = null;
        if (properties.isEmpty())
            properties = null;

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

    @Override
    public Object getProperty(String key) {
        if (properties == null)
            return null;
        return super.getProperty(key);
    }

    @Override
    public void setProperty(String key, Object value) {
        if (properties == null)
            properties = new HashMap<>();
        super.setProperty(key, value);
    }

    public void setDefaultContextObject(Class clz, Object value) {
        if (defaultContextObjects == null)
            defaultContextObjects = new HashMap<>();
        defaultContextObjects.put(clz, value);
    }
}
