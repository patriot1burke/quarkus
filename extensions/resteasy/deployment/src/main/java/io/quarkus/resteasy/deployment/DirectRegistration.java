package io.quarkus.resteasy.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;

import org.jboss.resteasy.spi.ResteasyDeployment;

import io.quarkus.resteasy.common.deployment.ProviderRegistrationHelper;
import io.quarkus.resteasy.common.runtime.ResteasyRegistrationRecorder;
import io.quarkus.resteasy.runtime.QuarkusResteasyDeployment;

public class DirectRegistration {

    // todo we should do this processing within server common
    public static void cleanScannedResource(ResteasyDeployment dep) throws Exception {
        ArrayList<String> scanned = new ArrayList<>();
        for (String res : dep.getScannedResourceClasses()) {
            Class resource = null;
            try {
                resource = Class.forName(res, false, Thread.currentThread().getContextClassLoader());
                if (resource.isInterface() || resource.isAnonymousClass()
                        || (resource.isMemberClass() && !Modifier.isStatic(resource.getModifiers()))
                //     || !Modifier.isPublic(resource.getModifiers())
                )
                    continue;
                if (!resource.isAnnotationPresent(javax.ws.rs.Path.class)) {
                    boolean hasPath = false;
                    for (Class intf : resource.getInterfaces()) {
                        if (intf.isAnnotationPresent(javax.ws.rs.Path.class)) {
                            hasPath = true;
                            break;
                        }
                    }
                    if (!hasPath)
                        continue;
                }
            } catch (ClassNotFoundException ignore) {
                // Kojito and maybe other extensions generate resource classes that are not on classpath
                // so just ignore
            }

            scanned.add(res);

        }
        dep.setScannedResourceClasses(scanned);
    }

    public static void registerProviders(ResteasyRegistrationRecorder recorder, QuarkusResteasyDeployment dep)
            throws Exception {
        for (String builtin : dep.getBuiltinProviderClasses()) {
            ProviderRegistrationHelper.processProvider(false, recorder, builtin, true);
        }
        for (String provider : dep.getScannedProviderClasses()) {

            ProviderRegistrationHelper.processProvider(false, recorder, provider, false);
        }
    }

}
