package io.quarkus.resteasy.common.deployment;

import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.client.RxInvokerProvider;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.ReaderInterceptor;
import javax.ws.rs.ext.RuntimeDelegate;
import javax.ws.rs.ext.WriterInterceptor;

import org.jboss.resteasy.core.MediaTypeMap;
import org.jboss.resteasy.core.providerfactory.SortedKey;
import org.jboss.resteasy.core.providerfactory.Utils;
import org.jboss.resteasy.spi.AsyncClientResponseProvider;
import org.jboss.resteasy.spi.AsyncResponseProvider;
import org.jboss.resteasy.spi.AsyncStreamProvider;
import org.jboss.resteasy.spi.ContextInjector;
import org.jboss.resteasy.spi.InjectorFactory;
import org.jboss.resteasy.spi.StringParameterUnmarshaller;
import org.jboss.resteasy.spi.metadata.ResourceClassProcessor;
import org.jboss.resteasy.spi.util.Types;

import io.quarkus.resteasy.common.runtime.ResteasyRegistrationRecorder;
import io.quarkus.runtime.RuntimeValue;

public class ProviderRegistrationHelper {

    public static void registerProviders(boolean clientOnly,
            ResteasyRegistrationRecorder recorder,
            JaxrsProvidersToRegisterBuildItem registry) throws Exception {
        for (String provider : registry.getBuiltin()) {
            processProvider(clientOnly, recorder, provider, true);
        }
        for (String provider : registry.getContributedProviders()) {
            processProvider(clientOnly, recorder, provider, true);
        }
        for (String provider : registry.getProviders()) {
            processProvider(clientOnly, recorder, provider, false);
        }

    }

    public static void processProvider(boolean clientOnly, ResteasyRegistrationRecorder recorder, String className,
            boolean builtin)
            throws Exception {
        Class<?> provider = null;
        try {
            provider = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            // Probably a generated class, just register it with vanilla register method
            recorder.registerProvider(className);
            return;
        }
        ConstrainedTo constrainedTo = (ConstrainedTo) provider.getAnnotation(ConstrainedTo.class);
        RuntimeType runtime = (constrainedTo == null) ? null : constrainedTo.value();
        if (clientOnly && runtime != null && runtime != RuntimeType.CLIENT)
            return;
        if (clientOnly)
            runtime = RuntimeType.CLIENT;

        int priority = Utils.getPriority(provider);

        if (MessageBodyReader.class.isAssignableFrom(provider)
                && MessageBodyWriter.class.isAssignableFrom(provider)) {
            RuntimeValue<Object> providerInstance = recorder.createProviderInstance(provider);
            addMessageBodyReader(recorder, provider, runtime, priority, providerInstance, builtin);
            addMessageBodyWriter(recorder, provider, runtime, priority, providerInstance, builtin);
        } else if (MessageBodyReader.class.isAssignableFrom(provider)) {
            addMessageBodyReader(recorder, provider, runtime, priority, builtin);
        } else if (MessageBodyWriter.class.isAssignableFrom(provider)) {
            addMessageBodyWriter(recorder, provider, runtime, priority, builtin);
        }

        if (ReaderInterceptor.class.isAssignableFrom(provider)) {
            recorder.registerReaderInterceptor(runtime, provider, priority);
        }
        if (WriterInterceptor.class.isAssignableFrom(provider)) {
            recorder.registerWriterInterceptor(runtime, provider, priority);
        }
        if (DynamicFeature.class.isAssignableFrom(provider)) {
            recorder.registerDynamicFeature(runtime, provider, priority);
        }
        if (ClientRequestFilter.class.isAssignableFrom(provider)) {
            recorder.registerClientRequestFilter(provider, priority);
        }
        if (ClientResponseFilter.class.isAssignableFrom(provider)) {
            recorder.registerClientResponseFilter(provider, priority);
        }
        if (AsyncClientResponseProvider.class.isAssignableFrom(provider)) {
            recorder.registerAsyncClientResponse(provider, priority);
        }
        if (RxInvokerProvider.class.isAssignableFrom(provider)) {
            recorder.registerReactiveClass(provider, priority);
        }
        if (!clientOnly && ContainerRequestFilter.class.isAssignableFrom(provider)) {
            recorder.registerContainerRequestFilter(provider, priority);
        }
        if (!clientOnly && ContainerResponseFilter.class.isAssignableFrom(provider)) {
            recorder.registerContainerResponseFilter(provider, priority);
        }
        if (!clientOnly && AsyncResponseProvider.class.isAssignableFrom(provider)) {
            recorder.registerAsyncResponseProvider(provider, priority);
        }
        if (!clientOnly && AsyncStreamProvider.class.isAssignableFrom(provider)) {
            recorder.registerAsyncStreamProvider(provider, priority);
        }
        if (!clientOnly && ExceptionMapper.class.isAssignableFrom(provider)) {
            recorder.registerExceptionMapper(provider, priority, builtin);
        }
        if (ParamConverterProvider.class.isAssignableFrom(provider)) {
            recorder.registerParamConverter(provider, priority, builtin);
        }
        if (ContextResolver.class.isAssignableFrom(provider)) {
            recorder.registerContextResolver(provider, priority, builtin);
        }
        if (ContextInjector.class.isAssignableFrom(provider)) {
            recorder.registerContextInjector(provider, priority);
        }
        if (StringParameterUnmarshaller.class.isAssignableFrom(provider)) {
            recorder.registerStringParameterUnmarshaller(provider, priority);
        }
        if (InjectorFactory.class.isAssignableFrom(provider)) {
            recorder.registerInjectorFactory(provider);
        }
        if (Feature.class.isAssignableFrom(provider) && (runtime == null || runtime == RuntimeType.SERVER)) {
            recorder.registerFeature(provider, priority);
        }
        if (ResourceClassProcessor.class.isAssignableFrom(provider)) {
            recorder.registerResourceClassProcessor(provider, priority);
        }
        if (RuntimeDelegate.HeaderDelegate.class.isAssignableFrom(provider)) {
            recorder.registerHeaderDelegate(provider);
        }
    }

    protected static void addMessageBodyReader(ResteasyRegistrationRecorder recorder, Class<?> provider, RuntimeType runtime,
            int priority, boolean builtin) {
        Class<?> template = Types.getTemplateParameterOfInterface(provider, MessageBodyReader.class);
        RuntimeValue<SortedKey> key = recorder.createSortedKey(provider, priority, template, builtin);
        Consumes consumeMime = provider.getClass().getAnnotation(Consumes.class);
        if (consumeMime != null) {
            for (String consume : consumeMime.value()) {
                RuntimeValue<MediaType> mediaTypeRef = recorder.createMediaType(consume.toLowerCase());
                MediaType mediaType = MediaType.valueOf(consume.toLowerCase());
                String baseSubtype = null;
                if (mediaType.isWildcardType()) {
                    recorder.registerMessageBodyReaderWildcard(runtime, provider, key);
                } else if (mediaType.isWildcardSubtype()) {
                    recorder.registerMessageBodyReaderSubtypeWildcard(runtime, provider, mediaTypeRef, key);
                } else if ((baseSubtype = MediaTypeMap.compositeWildSubtype(mediaType.getSubtype())) != null) {
                    recorder.registerMessageBodyReaderSubtypeCompositeWildcard(runtime, provider, mediaTypeRef, baseSubtype,
                            key);
                } else if ((baseSubtype = MediaTypeMap.compositeWildSubtype(mediaType.getSubtype())) != null) {
                    recorder.registerMessageBodyReaderSubtypeWildcardComposite(runtime, provider, mediaTypeRef, baseSubtype,
                            key);
                } else {
                    recorder.registerMessageBodyReader(runtime, provider, mediaTypeRef, key);
                }
            }
        } else {
            recorder.registerMessageBodyReaderWildcard(runtime, provider, key);
        }
    }

    protected static void addMessageBodyWriter(ResteasyRegistrationRecorder recorder, Class<?> provider, RuntimeType runtime,
            int priority, boolean builtin) {
        Class<?> template = Types.getTemplateParameterOfInterface(provider, MessageBodyWriter.class);
        RuntimeValue<SortedKey> key = recorder.createSortedKey(provider, priority, template, builtin);
        Produces produceMime = provider.getClass().getAnnotation(Produces.class);
        if (produceMime != null) {
            for (String produces : produceMime.value()) {
                RuntimeValue<MediaType> mediaTypeRef = recorder.createMediaType(produces.toLowerCase());
                MediaType mediaType = MediaType.valueOf(produces.toLowerCase());
                String baseSubtype = null;
                if (mediaType.isWildcardType()) {
                    recorder.registerMessageBodyWriterWildcard(runtime, provider, key);
                } else if (mediaType.isWildcardSubtype()) {
                    recorder.registerMessageBodyWriterSubtypeWildcard(runtime, provider, mediaTypeRef, key);
                } else if ((baseSubtype = MediaTypeMap.compositeWildSubtype(mediaType.getSubtype())) != null) {
                    recorder.registerMessageBodyWriterSubtypeCompositeWildcard(runtime, provider, mediaTypeRef, baseSubtype,
                            key);
                } else if ((baseSubtype = MediaTypeMap.compositeWildSubtype(mediaType.getSubtype())) != null) {
                    recorder.registerMessageBodyWriterSubtypeWildcardComposite(runtime, provider, mediaTypeRef, baseSubtype,
                            key);
                } else {
                    recorder.registerMessageBodyWriter(runtime, provider, mediaTypeRef, key);
                }
            }
        } else {
            recorder.registerMessageBodyWriterWildcard(runtime, provider, key);
        }
    }

    protected static void addMessageBodyReader(ResteasyRegistrationRecorder recorder, Class<?> provider, RuntimeType runtime,
            int priority, RuntimeValue<Object> providerInstance, boolean builtin) {
        Class<?> template = Types.getTemplateParameterOfInterface(provider, MessageBodyReader.class);
        Consumes consumeMime = provider.getClass().getAnnotation(Consumes.class);
        if (consumeMime != null) {
            for (String consume : consumeMime.value()) {
                RuntimeValue<MediaType> mediaTypeRef = recorder.createMediaType(consume.toLowerCase());
                MediaType mediaType = MediaType.valueOf(consume.toLowerCase());
                String baseSubtype = null;
                if (mediaType.isWildcardType()) {
                    recorder.registerMessageBodyReaderWildcard(runtime, provider, providerInstance, priority, template,
                            builtin);
                } else if (mediaType.isWildcardSubtype()) {
                    recorder.registerMessageBodyReaderSubtypeWildcard(runtime, provider, mediaTypeRef, providerInstance,
                            priority, template, builtin);
                } else if ((baseSubtype = MediaTypeMap.compositeWildSubtype(mediaType.getSubtype())) != null) {
                    recorder.registerMessageBodyReaderSubtypeCompositeWildcard(runtime, provider, mediaTypeRef, baseSubtype,
                            providerInstance, priority, template, builtin);
                } else if ((baseSubtype = MediaTypeMap.compositeWildSubtype(mediaType.getSubtype())) != null) {
                    recorder.registerMessageBodyReaderSubtypeWildcardComposite(runtime, provider, mediaTypeRef, baseSubtype,
                            providerInstance, priority, template, builtin);
                } else {
                    recorder.registerMessageBodyReader(runtime, provider, mediaTypeRef, providerInstance, priority, template,
                            builtin);
                }
            }
        } else {
            recorder.registerMessageBodyReaderWildcard(runtime, provider, providerInstance, priority, template, builtin);
        }
    }

    protected static void addMessageBodyWriter(ResteasyRegistrationRecorder recorder, Class<?> provider, RuntimeType runtime,
            int priority, RuntimeValue<Object> providerInstance, boolean builtin) {
        Class<?> template = Types.getTemplateParameterOfInterface(provider, MessageBodyWriter.class);
        Produces produceMime = provider.getClass().getAnnotation(Produces.class);
        if (produceMime != null) {
            for (String produces : produceMime.value()) {
                RuntimeValue<MediaType> mediaTypeRef = recorder.createMediaType(produces.toLowerCase());
                MediaType mediaType = MediaType.valueOf(produces.toLowerCase());
                String baseSubtype = null;
                if (mediaType.isWildcardType()) {
                    recorder.registerMessageBodyWriterWildcard(runtime, provider, providerInstance, priority, template,
                            builtin);
                } else if (mediaType.isWildcardSubtype()) {
                    recorder.registerMessageBodyWriterSubtypeWildcard(runtime, provider, mediaTypeRef, providerInstance,
                            priority, template, builtin);
                } else if ((baseSubtype = MediaTypeMap.compositeWildSubtype(mediaType.getSubtype())) != null) {
                    recorder.registerMessageBodyWriterSubtypeCompositeWildcard(runtime, provider, mediaTypeRef, baseSubtype,
                            providerInstance, priority, template, builtin);
                } else if ((baseSubtype = MediaTypeMap.compositeWildSubtype(mediaType.getSubtype())) != null) {
                    recorder.registerMessageBodyWriterSubtypeWildcardComposite(runtime, provider, mediaTypeRef, baseSubtype,
                            providerInstance, priority, template, builtin);
                } else {
                    recorder.registerMessageBodyWriter(runtime, provider, mediaTypeRef, providerInstance, priority, template,
                            builtin);
                }
            }
        } else {
            recorder.registerMessageBodyWriterWildcard(runtime, provider, providerInstance, priority, template, builtin);
        }
    }
}
