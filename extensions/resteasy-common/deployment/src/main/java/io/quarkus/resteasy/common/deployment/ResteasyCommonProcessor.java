package io.quarkus.resteasy.common.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.ws.rs.core.MediaType;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;
import org.jboss.resteasy.plugins.interceptors.AcceptEncodingGZIPFilter;
import org.jboss.resteasy.plugins.interceptors.GZIPDecodingInterceptor;
import org.jboss.resteasy.plugins.interceptors.GZIPEncodingInterceptor;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.spi.InjectorFactory;

import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ProxyUnwrapperBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.resteasy.common.runtime.ResteasyInjectorFactoryRecorder;
import io.quarkus.resteasy.common.spi.ResteasyJaxrsProviderBuildItem;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.quarkus.runtime.configuration.MemorySize;

public class ResteasyCommonProcessor {
    private static final Logger LOGGER = Logger.getLogger(ResteasyCommonProcessor.class.getName());

    private static final ProviderDiscoverer[] PROVIDER_DISCOVERERS = {
            new ProviderDiscoverer(ResteasyDotNames.GET, false, true),
            new ProviderDiscoverer(ResteasyDotNames.HEAD, false, false),
            new ProviderDiscoverer(ResteasyDotNames.DELETE, true, false),
            new ProviderDiscoverer(ResteasyDotNames.OPTIONS, false, true),
            new ProviderDiscoverer(ResteasyDotNames.PATCH, true, false),
            new ProviderDiscoverer(ResteasyDotNames.POST, true, true),
            new ProviderDiscoverer(ResteasyDotNames.PUT, true, false)
    };

    private ResteasyCommonConfig resteasyCommonConfig;

    @ConfigRoot(name = "resteasy")
    public static final class ResteasyCommonConfig {
        /**
         * Enable gzip support for REST
         */
        public ResteasyCommonConfigGzip gzip;

        /**
         * If true, any provider classes defined in META-INF/services/javax.ws.rs.ext.Providers
         * will be registered and used. Defaults to true.
         */
        @ConfigItem(defaultValue = "true")
        public boolean useBuiltinProviders;

        /**
         * If true, some core providers are pruned from registration.
         * There are a lot of providers included in resteasy-core that are rarely used.
         * If you are using one of the pruned providers, set this flag to false.
         *
         * The following providers are pruned by default:
         * <ul>
         * <li>org.jboss.resteasy.plugins.providers.DataSourceProvider</li>
         * <li>org.jboss.resteasy.plugins.providers.DocumentProvider</li>
         * <li>org.jboss.resteasy.plugins.providers.DefaultNumberWriter</li>
         * <li>org.jboss.resteasy.plugins.providers.DefaultBooleanWriter</li>
         * <li>org.jboss.resteasy.plugins.providers.SourceProvider</li>
         * <li>org.jboss.resteasy.plugins.providers.FileRangeWriter</li>
         * <li>org.jboss.resteasy.plugins.providers.IIOImageProvider</li>
         * <li>org.jboss.resteasy.plugins.providers.ReaderProvider</li>
         * <li>org.jboss.resteasy.plugins.providers.FileProvider</li>
         * <li>org.jboss.resteasy.plugins.interceptors.ClientContentEncodingAnnotationFeature</li>
         * <li>org.jboss.resteasy.plugins.interceptors.ServerContentEncodingAnnotationFeature</li>
         * <li>org.jboss.resteasy.plugins.interceptors.MessageSanitizerContainerResponseFilter</li>
         * </ul>
         */
        @ConfigItem(defaultValue = "true")
        public boolean pruneCoreProviders;
    }

    @ConfigGroup
    public static final class ResteasyCommonConfigGzip {
        /**
         * If gzip is enabled
         */
        @ConfigItem
        public boolean enabled;
        /**
         * Maximum deflated file bytes size
         * <p>
         * If the limit is exceeded, Resteasy will return Response
         * with status 413("Request Entity Too Large")
         */
        @ConfigItem(defaultValue = "10M")
        public MemorySize maxInput;
    }

    @BuildStep
    void setupGzipProviders(BuildProducer<ResteasyJaxrsProviderBuildItem> providers) {
        // If GZIP support is enabled, enable it
        if (resteasyCommonConfig.gzip.enabled) {
            providers.produce(new ResteasyJaxrsProviderBuildItem(AcceptEncodingGZIPFilter.class.getName()));
            providers.produce(new ResteasyJaxrsProviderBuildItem(GZIPDecodingInterceptor.class.getName()));
            providers.produce(new ResteasyJaxrsProviderBuildItem(GZIPEncodingInterceptor.class.getName()));
        }
    }

    @Record(STATIC_INIT)
    @BuildStep
    ResteasyInjectionReadyBuildItem setupResteasyInjection(List<ProxyUnwrapperBuildItem> proxyUnwrappers,
            BeanContainerBuildItem beanContainerBuildItem,
            ResteasyInjectorFactoryRecorder recorder) {
        List<Function<Object, Object>> unwrappers = new ArrayList<>();
        for (ProxyUnwrapperBuildItem i : proxyUnwrappers) {
            unwrappers.add(i.getUnwrapper());
        }
        RuntimeValue<InjectorFactory> injectorFactory = recorder.setup(beanContainerBuildItem.getValue(), unwrappers);
        return new ResteasyInjectionReadyBuildItem(injectorFactory);
    }

    private static final HashSet<String> prunedCoreProviders = new HashSet<>();

    static {
        prunedCoreProviders.add("org.jboss.resteasy.plugins.providers.DataSourceProvider");
        prunedCoreProviders.add("org.jboss.resteasy.plugins.providers.DocumentProvider");
        prunedCoreProviders.add("org.jboss.resteasy.plugins.providers.DefaultNumberWriter");
        prunedCoreProviders.add("org.jboss.resteasy.plugins.providers.DefaultBooleanWriter");
        prunedCoreProviders.add("org.jboss.resteasy.plugins.providers.SourceProvider");
        prunedCoreProviders.add("org.jboss.resteasy.plugins.providers.FileRangeWriter");
        prunedCoreProviders.add("org.jboss.resteasy.plugins.providers.IIOImageProvider");
        prunedCoreProviders.add("org.jboss.resteasy.plugins.providers.ReaderProvider");
        prunedCoreProviders.add("org.jboss.resteasy.plugins.providers.FileProvider");
        // todo this is tested for some reason, let's move the test
        //  prunedCoreProviders.add("org.jboss.resteasy.plugins.interceptors.CacheControlFeature");
        prunedCoreProviders.add("org.jboss.resteasy.plugins.interceptors.ClientContentEncodingAnnotationFeature");
        prunedCoreProviders.add("org.jboss.resteasy.plugins.interceptors.ServerContentEncodingAnnotationFeature");
        prunedCoreProviders.add("org.jboss.resteasy.plugins.interceptors.MessageSanitizerContainerResponseFilter");
    }

    @BuildStep
    public JaxrsProvidersToRegisterBuildItem registerProviders(CombinedIndexBuildItem combinedIndexBuildItem,
            List<ResteasyJaxrsProviderBuildItem> contributedProviderBuildItems,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans,
            Capabilities capabilities) throws Exception {
        if (!capabilities.isCapabilityPresent(Capabilities.RESTEASY_JSON_EXTENSION)) {

            boolean needJsonSupport = restJsonSupportNeeded(combinedIndexBuildItem, ResteasyDotNames.CONSUMES)
                    || restJsonSupportNeeded(combinedIndexBuildItem, ResteasyDotNames.PRODUCES);
            if (needJsonSupport) {
                LOGGER.warn(
                        "Quarkus detected the need of REST JSON support but you have not provided the necessary JSON " +
                                "extension for this. You can visit https://quarkus.io/guides/rest-json for more " +
                                "information on how to set one.");
            }
        }

        JaxrsProvidersToRegisterBuildItem providers = new JaxrsProvidersToRegisterBuildItem();
        // providers
        Set<String> allProviders = new HashSet<>();
        for (AnnotationInstance i : combinedIndexBuildItem.getIndex().getAnnotations(ResteasyDotNames.PROVIDER)) {
            if (i.target().kind() == AnnotationTarget.Kind.CLASS) {
                String provider = i.target().asClass().name().toString();
                allProviders.add(provider);
                providers.getProviders().add(provider);
            }
        }
        if (resteasyCommonConfig.useBuiltinProviders) {
            Map<String, URL> classMap = RegisterBuiltin.scanBuiltins();
            for (String builtin : classMap.keySet()) {
                if (resteasyCommonConfig.pruneCoreProviders && prunedCoreProviders.contains(builtin)) {
                    continue;
                }
                allProviders.add(builtin);
                providers.getBuiltin().add(builtin);
            }
        }

        if (resteasyCommonConfig.gzip.enabled) {
            allProviders.add(AcceptEncodingGZIPFilter.class.getName());
            providers.getBuiltin().add(AcceptEncodingGZIPFilter.class.getName());
            allProviders.add(GZIPDecodingInterceptor.class.getName());
            providers.getBuiltin().add(GZIPDecodingInterceptor.class.getName());
            allProviders.add(GZIPEncodingInterceptor.class.getName());
            providers.getBuiltin().add(GZIPEncodingInterceptor.class.getName());
        }

        for (ResteasyJaxrsProviderBuildItem contributed : contributedProviderBuildItems) {
            if (!allProviders.contains(contributed.getName())) {
                allProviders.add(contributed.getName());
                providers.getContributedProviders().add(contributed.getName());
            }
        }

        for (String providerToRegister : allProviders) {
            reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, providerToRegister));
        }

        // Providers that are also beans are unremovable
        unremovableBeans.produce(new UnremovableBeanBuildItem(
                b -> allProviders.contains(b.getBeanClass().toString())));

        if (allProviders.contains("org.jboss.resteasy.plugins.providers.jsonb.JsonBindingProvider")) {
            // This abstract one is also accessed directly via reflection
            reflectiveClass.produce(new ReflectiveClassBuildItem(true, true,
                    "org.jboss.resteasy.plugins.providers.jsonb.AbstractJsonBindingProvider"));
        }
        return providers;
    }

    private boolean restJsonSupportNeeded(CombinedIndexBuildItem indexBuildItem, DotName mediaTypeAnnotation) {
        for (AnnotationInstance annotationInstance : indexBuildItem.getIndex().getAnnotations(mediaTypeAnnotation)) {
            final AnnotationValue annotationValue = annotationInstance.value();
            if (annotationValue == null) {
                continue;
            }

            final List<String> mediaTypes = Arrays.asList(annotationValue.asStringArray());
            return mediaTypes.contains(MediaType.APPLICATION_JSON)
                    || mediaTypes.contains(MediaType.APPLICATION_JSON_PATCH_JSON);
        }

        return false;
    }

    private static class ProviderDiscoverer {

        private final DotName methodAnnotation;

        private final boolean noConsumesDefaultsToAll;

        private final boolean noProducesDefaultsToAll;

        private ProviderDiscoverer(DotName methodAnnotation, boolean noConsumesDefaultsToAll,
                boolean noProducesDefaultsToAll) {
            this.methodAnnotation = methodAnnotation;
            this.noConsumesDefaultsToAll = noConsumesDefaultsToAll;
            this.noProducesDefaultsToAll = noProducesDefaultsToAll;
        }

        public DotName getMethodAnnotation() {
            return methodAnnotation;
        }

        public boolean noConsumesDefaultsToAll() {
            return noConsumesDefaultsToAll;
        }

        public boolean noProducesDefaultsToAll() {
            return noProducesDefaultsToAll;
        }
    }
}
