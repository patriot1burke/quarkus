package io.quarkus.resteasy.runtime.standalone;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;
import org.jboss.resteasy.core.providerfactory.SortedKey;
import org.jboss.resteasy.core.providerfactory.Utils;
import org.jboss.resteasy.spi.AsyncClientResponseProvider;
import org.jboss.resteasy.spi.AsyncResponseProvider;
import org.jboss.resteasy.spi.AsyncStreamProvider;
import org.jboss.resteasy.spi.ContextInjector;
import org.jboss.resteasy.spi.InjectorFactory;
import org.jboss.resteasy.spi.StringParameterUnmarshaller;
import org.jboss.resteasy.spi.metadata.ResourceClassProcessor;

import io.quarkus.resteasy.common.runtime.QuarkusInjectorFactory;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ResteasyRegistrationRecorder {

    private static QuarkusResteasyDeployment deployment;

    public void initialize(String application) {
        deployment = new QuarkusResteasyDeployment();
        deployment.setApplicationClass(application);
        deployment.setInjectorFactoryClass(QuarkusInjectorFactory.class.getName());
        deployment.initialize();
    }

    public void startDeployment(List<String> resourceClasses, String path, Set<String> known) {
        deployment.setResourceClasses(resourceClasses);
        ResteasyStandaloneRecorder.startDeployment(deployment, path, known);
        deployment = null;
    }

    public RuntimeValue<MediaType> createMediaType(String mediaType) {
        return new RuntimeValue<MediaType>(MediaType.valueOf(mediaType));
    }

    public RuntimeValue<SortedKey> createSortedKey(Class providerClass, int priority, Class<?> template, boolean builtin) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        Object provider = Utils.createProviderInstance(providerFactory, providerClass);
        Utils.injectProperties(providerFactory, providerClass, provider);
        SortedKey key = new SortedKey(provider, builtin, template, priority);
        return new RuntimeValue<SortedKey>(key);
    }

    public RuntimeValue<Object> createProviderInstance(Class providerClass) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        Object providerInstance = Utils.createProviderInstance(providerFactory, providerClass);
        Utils.injectProperties(providerFactory, providerClass, providerInstance);
        return new RuntimeValue<>(providerInstance);
    }

    private void processContracts(Class<?> providerClass, ResteasyProviderFactoryImpl providerFactory, int priority,
            Class<?> contract) {
        providerFactory.getMutableProviderClasses().add(providerClass);
        Map<Class<?>, Map<Class<?>, Integer>> classContracts = providerFactory.getClassContracts();
        Map<Class<?>, Integer> map = classContracts.get(providerClass);
        if (map == null) {
            map = new HashMap<>();
            classContracts.put(providerClass, map);
        }
        map.put(contract, priority);
    }

    public void registerMessageBodyReader(RuntimeType runtimeType, Class<?> providerClass, RuntimeValue<MediaType> mediaType,
            RuntimeValue<SortedKey> key) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyReader> sortedKey = key.getValue();
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addRegularMBR(mediaType.getValue(), sortedKey);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addRegularMBR(mediaType.getValue(), sortedKey);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyReader.class);
    }

    public void registerMessageBodyReaderWildcard(RuntimeType runtimeType, Class<?> providerClass,
            RuntimeValue<SortedKey> key) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyReader> sortedKey = (SortedKey<MessageBodyReader>) key.getValue();
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addWildcardMBR(sortedKey);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addWildcardMBR(sortedKey);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyReader.class);
    }

    public void registerMessageBodyReaderSubtypeWildcard(RuntimeType runtimeType, Class<?> providerClass,
            RuntimeValue<MediaType> mediaType, RuntimeValue<SortedKey> key) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyReader> sortedKey = key.getValue();
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addSubtypeWildMBR(mediaType.getValue(), sortedKey);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addSubtypeWildMBR(mediaType.getValue(), sortedKey);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyReader.class);
    }

    public void registerMessageBodyReaderSubtypeWildcardComposite(RuntimeType runtimeType, Class<?> providerClass,
            RuntimeValue<MediaType> mediaType, String baseSubtype, RuntimeValue<SortedKey> key) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyReader> sortedKey = key.getValue();
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addWildcardCompositeMBR(mediaType.getValue(), sortedKey, baseSubtype);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addWildcardCompositeMBR(mediaType.getValue(), sortedKey, baseSubtype);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyReader.class);
    }

    public void registerMessageBodyReaderSubtypeCompositeWildcard(RuntimeType runtimeType, Class<?> providerClass,
            RuntimeValue<MediaType> mediaType, String baseSubtype, RuntimeValue<SortedKey> key) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyReader> sortedKey = key.getValue();
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addCompositeWildcardMBR(mediaType.getValue(), sortedKey, baseSubtype);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addCompositeWildcardMBR(mediaType.getValue(), sortedKey, baseSubtype);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyReader.class);
    }

    public void registerMessageBodyWriter(RuntimeType runtimeType, Class<?> providerClass, RuntimeValue<MediaType> mediaType,
            RuntimeValue<SortedKey> key) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyWriter> sortedKey = key.getValue();
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addRegularMBW(mediaType.getValue(), sortedKey);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addRegularMBW(mediaType.getValue(), sortedKey);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyWriter.class);
    }

    public void registerMessageBodyWriterWildcard(RuntimeType runtimeType, Class<?> providerClass,
            RuntimeValue<SortedKey> key) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyWriter> sortedKey = key.getValue();
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addWildcardMBW(sortedKey);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addWildcardMBW(sortedKey);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyWriter.class);
    }

    public void registerMessageBodyWriterSubtypeWildcard(RuntimeType runtimeType, Class<?> providerClass,
            RuntimeValue<MediaType> mediaType, RuntimeValue<SortedKey> key) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyWriter> sortedKey = key.getValue();
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addSubtypeWildMBW(mediaType.getValue(), sortedKey);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addSubtypeWildMBW(mediaType.getValue(), sortedKey);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyWriter.class);
    }

    public void registerMessageBodyWriterSubtypeWildcardComposite(RuntimeType runtimeType, Class<?> providerClass,
            RuntimeValue<MediaType> mediaType, String baseSubtype, RuntimeValue<SortedKey> key) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyWriter> sortedKey = key.getValue();
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addWildcardCompositeMBW(mediaType.getValue(), sortedKey, baseSubtype);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addWildcardCompositeMBW(mediaType.getValue(), sortedKey, baseSubtype);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyWriter.class);
    }

    public void registerMessageBodyWriterSubtypeCompositeWildcard(RuntimeType runtimeType, Class<?> providerClass,
            RuntimeValue<MediaType> mediaType, String baseSubtype, RuntimeValue<SortedKey> key) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyWriter> sortedKey = key.getValue();
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addCompositeWildcardMBW(mediaType.getValue(), sortedKey, baseSubtype);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addCompositeWildcardMBW(mediaType.getValue(), sortedKey, baseSubtype);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyWriter.class);
    }

    public void registerReaderInterceptor(RuntimeType runtimeType, Class providerClass, int priority) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addReaderInterceptor(providerClass, priority);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addReaderInterceptor(providerClass, priority);
        processContracts(providerClass, providerFactory, priority, ReaderInterceptor.class);
    }

    public void registerWriterInterceptor(RuntimeType runtimeType, Class providerClass, int priority) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addReaderInterceptor(providerClass, priority);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addReaderInterceptor(providerClass, priority);
        processContracts(providerClass, providerFactory, priority, ReaderInterceptor.class);
    }

    public void registerDynamicFeature(RuntimeType runtimeType, Class providerClass, int priority) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addDynamicFeature(providerClass);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addDynamicFeature(providerClass);
        processContracts(providerClass, providerFactory, priority, DynamicFeature.class);
    }

    public void registerContainerRequestFilter(Class providerClass, int priority) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.getServerHelper().addContainerRequestFilter(providerClass, priority);
        processContracts(providerClass, providerFactory, priority, ContainerRequestFilter.class);
    }

    public void registerContainerResponseFilter(Class providerClass, int priority) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.getServerHelper().addContainerResponseFilter(providerClass, priority);
        processContracts(providerClass, providerFactory, priority, ContainerResponseFilter.class);
    }

    public void registerAsyncResponseProvider(Class providerClass, int priority) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.getServerHelper().addAsyncResponseProvider(providerClass);
        processContracts(providerClass, providerFactory, priority, AsyncResponseProvider.class);
    }

    public void registerAsyncStreamProvider(Class providerClass, int priority) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.getServerHelper().addAsyncStreamProvider(providerClass);
        processContracts(providerClass, providerFactory, priority, AsyncStreamProvider.class);

    }

    public void registerExceptionMapper(Class providerClass, int priority, boolean builtIn) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.getServerHelper().addExceptionMapper(providerClass, builtIn);
        processContracts(providerClass, providerFactory, priority, ExceptionMapper.class);
    }

    public void registerClientRequestFilter(Class providerClass, int priority) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.getClientHelper().addClientRequestFilter(providerClass, priority);
        processContracts(providerClass, providerFactory, priority, ClientRequestFilter.class);
    }

    public void registerClientResponseFilter(Class providerClass, int priority) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.getClientHelper().addClientResponseFilter(providerClass, priority);
        processContracts(providerClass, providerFactory, priority, ClientResponseFilter.class);
    }

    public void registerAsyncClientResponse(Class providerClass, int priority) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.getClientHelper().addAsyncClientResponseProvider(providerClass);
        processContracts(providerClass, providerFactory, priority, AsyncClientResponseProvider.class);
    }

    public void registerReactiveClass(Class providerClass, int priority) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.getClientHelper().addReactiveClass(providerClass);
        processContracts(providerClass, providerFactory, priority, RxInvokerProvider.class);
    }

    public void registerParamConverter(Class providerClass, int priority, boolean builtin) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.addParameterConverterProvider(providerClass, builtin, priority);
        processContracts(providerClass, providerFactory, priority, ParamConverterProvider.class);
    }

    public void registerContextResolver(Class providerClass, int priority, boolean builtin) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.addContextResolver(providerClass, builtin, priority);
        processContracts(providerClass, providerFactory, priority, ContextResolver.class);
    }

    public void registerContextInjector(Class providerClass, int priority) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.addContextInjector(providerClass);
        processContracts(providerClass, providerFactory, priority, ContextInjector.class);
    }

    public void registerStringParameterUnmarshaller(Class providerClass, int priority) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.addStringParameterUnmarshaller(providerClass);
        processContracts(providerClass, providerFactory, priority, StringParameterUnmarshaller.class);
    }

    public void registerInjectorFactory(Class providerClass) throws Exception {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.addInjectorFactory(providerClass);
        processContracts(providerClass, providerFactory, 0, InjectorFactory.class);
    }

    public void registerFeature(Class providerClass, int priority) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.addFeature(providerClass);
        processContracts(providerClass, providerFactory, priority, Feature.class);
    }

    public void registerResourceClassProcessor(Class providerClass, int priority) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.addResourceClassProcessor(providerClass, priority);
        processContracts(providerClass, providerFactory, priority, ResourceClassProcessor.class);
    }

    public void registerHeaderDelegate(Class providerClass) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        providerFactory.addHeaderDelegate(providerClass);
    }

    //////////////////////

    public void registerMessageBodyReader(RuntimeType runtimeType,
            Class<?> providerClass,
            RuntimeValue<MediaType> mediaType,
            RuntimeValue<Object> provider,
            int priority,
            Class<?> template,
            boolean builtin) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyReader> sortedKey = new SortedKey<>((MessageBodyReader) provider.getValue(), builtin, template,
                priority);
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addRegularMBR(mediaType.getValue(), sortedKey);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addRegularMBR(mediaType.getValue(), sortedKey);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyReader.class);
    }

    public void registerMessageBodyReaderWildcard(RuntimeType runtimeType,
            Class<?> providerClass,
            RuntimeValue<Object> provider,
            int priority,
            Class<?> template,
            boolean builtin) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyReader> sortedKey = new SortedKey<>((MessageBodyReader) provider.getValue(), builtin, template,
                priority);
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addWildcardMBR(sortedKey);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addWildcardMBR(sortedKey);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyReader.class);
    }

    public void registerMessageBodyReaderSubtypeWildcard(RuntimeType runtimeType,
            Class<?> providerClass,
            RuntimeValue<MediaType> mediaType,
            RuntimeValue<Object> provider,
            int priority,
            Class<?> template,
            boolean builtin) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyReader> sortedKey = new SortedKey<>((MessageBodyReader) provider.getValue(), builtin, template,
                priority);
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addSubtypeWildMBR(mediaType.getValue(), sortedKey);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addSubtypeWildMBR(mediaType.getValue(), sortedKey);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyReader.class);
    }

    public void registerMessageBodyReaderSubtypeWildcardComposite(RuntimeType runtimeType,
            Class<?> providerClass,
            RuntimeValue<MediaType> mediaType,
            String baseSubtype,
            RuntimeValue<Object> provider,
            int priority,
            Class<?> template,
            boolean builtin) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyReader> sortedKey = new SortedKey<>((MessageBodyReader) provider.getValue(), builtin, template,
                priority);
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addWildcardCompositeMBR(mediaType.getValue(), sortedKey, baseSubtype);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addWildcardCompositeMBR(mediaType.getValue(), sortedKey, baseSubtype);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyReader.class);
    }

    public void registerMessageBodyReaderSubtypeCompositeWildcard(RuntimeType runtimeType,
            Class<?> providerClass,
            RuntimeValue<MediaType> mediaType,
            String baseSubtype,
            RuntimeValue<Object> provider,
            int priority,
            Class<?> template,
            boolean builtin) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyReader> sortedKey = new SortedKey<>((MessageBodyReader) provider.getValue(), builtin, template,
                priority);
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addCompositeWildcardMBR(mediaType.getValue(), sortedKey, baseSubtype);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addCompositeWildcardMBR(mediaType.getValue(), sortedKey, baseSubtype);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyReader.class);
    }

    public void registerMessageBodyWriter(RuntimeType runtimeType,
            Class<?> providerClass,
            RuntimeValue<MediaType> mediaType,
            RuntimeValue<Object> provider,
            int priority,
            Class<?> template,
            boolean builtin) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyWriter> sortedKey = new SortedKey<>((MessageBodyWriter) provider.getValue(), builtin, template,
                priority);
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addRegularMBW(mediaType.getValue(), sortedKey);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addRegularMBW(mediaType.getValue(), sortedKey);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyWriter.class);
    }

    public void registerMessageBodyWriterWildcard(RuntimeType runtimeType,
            Class<?> providerClass,
            RuntimeValue<Object> provider,
            int priority,
            Class<?> template,
            boolean builtin) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyWriter> sortedKey = new SortedKey<>((MessageBodyWriter) provider.getValue(), builtin, template,
                priority);
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addWildcardMBW(sortedKey);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addWildcardMBW(sortedKey);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyWriter.class);
    }

    public void registerMessageBodyWriterSubtypeWildcard(RuntimeType runtimeType,
            Class<?> providerClass,
            RuntimeValue<MediaType> mediaType,
            RuntimeValue<Object> provider,
            int priority,
            Class<?> template,
            boolean builtin) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyWriter> sortedKey = new SortedKey<>((MessageBodyWriter) provider.getValue(), builtin, template,
                priority);
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addSubtypeWildMBW(mediaType.getValue(), sortedKey);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addSubtypeWildMBW(mediaType.getValue(), sortedKey);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyWriter.class);
    }

    public void registerMessageBodyWriterSubtypeWildcardComposite(RuntimeType runtimeType,
            Class<?> providerClass,
            RuntimeValue<MediaType> mediaType,
            String baseSubtype,
            RuntimeValue<Object> provider,
            int priority,
            Class<?> template,
            boolean builtin) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyWriter> sortedKey = new SortedKey<>((MessageBodyWriter) provider.getValue(), builtin, template,
                priority);
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addWildcardCompositeMBW(mediaType.getValue(), sortedKey, baseSubtype);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addWildcardCompositeMBW(mediaType.getValue(), sortedKey, baseSubtype);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyWriter.class);
    }

    public void registerMessageBodyWriterSubtypeCompositeWildcard(RuntimeType runtimeType,
            Class<?> providerClass,
            RuntimeValue<MediaType> mediaType,
            String baseSubtype,
            RuntimeValue<Object> provider,
            int priority,
            Class<?> template,
            boolean builtin) {
        ResteasyProviderFactoryImpl providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
        SortedKey<MessageBodyWriter> sortedKey = new SortedKey<>((MessageBodyWriter) provider.getValue(), builtin, template,
                priority);
        if (runtimeType == null || runtimeType == RuntimeType.CLIENT)
            providerFactory.getClientHelper().addCompositeWildcardMBW(mediaType.getValue(), sortedKey, baseSubtype);
        if (runtimeType == null || runtimeType == RuntimeType.SERVER)
            providerFactory.getServerHelper().addCompositeWildcardMBW(mediaType.getValue(), sortedKey, baseSubtype);
        processContracts(providerClass, providerFactory, sortedKey.getPriority(), MessageBodyWriter.class);
    }

}
