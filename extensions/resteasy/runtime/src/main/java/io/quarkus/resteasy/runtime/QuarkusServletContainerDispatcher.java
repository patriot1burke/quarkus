package io.quarkus.resteasy.runtime;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.SecurityContext;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.core.ThreadLocalResteasyProviderFactory;
import org.jboss.resteasy.plugins.server.servlet.HttpRequestFactory;
import org.jboss.resteasy.plugins.server.servlet.HttpResponseFactory;
import org.jboss.resteasy.plugins.server.servlet.ServletSecurityContext;
import org.jboss.resteasy.plugins.server.servlet.ServletUtil;
import org.jboss.resteasy.resteasy_jaxrs.i18n.LogMessages;
import org.jboss.resteasy.specimpl.ResteasyHttpHeaders;
import org.jboss.resteasy.specimpl.ResteasyUriInfo;
import org.jboss.resteasy.spi.Dispatcher;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

public class QuarkusServletContainerDispatcher {
    protected Dispatcher dispatcher;
    protected ResteasyProviderFactory providerFactory;
    private String servletMappingPrefix = "";
    protected ResteasyDeployment deployment = null;
    protected HttpRequestFactory requestFactory;
    protected HttpResponseFactory responseFactory;

    protected ServletConfig servletConfig;

    public QuarkusServletContainerDispatcher(final ServletConfig servletConfig) {
        this.servletConfig = servletConfig;
    }

    public QuarkusServletContainerDispatcher() {
    }

    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    public void init(ResteasyDeployment deployment, String servletMappingPrefix, HttpRequestFactory requestFactory,
            HttpResponseFactory responseFactory) {
        this.requestFactory = requestFactory;
        this.responseFactory = responseFactory;
        if (servletMappingPrefix == null)
            servletMappingPrefix = "";
        servletMappingPrefix = servletMappingPrefix.trim();
        this.servletMappingPrefix = servletMappingPrefix;
        this.deployment = deployment;
        dispatcher = deployment.getDispatcher();
        providerFactory = deployment.getProviderFactory();
    }

    public void destroy() {
        if (deployment != null) {
            deployment.stop();
        }
    }

    public void setDispatcher(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void service(String httpMethod, HttpServletRequest request, HttpServletResponse response, boolean handleNotFound)
            throws IOException, NotFoundException {
        try {
            //logger.info(httpMethod + " " + request.getRequestURL().toString());
            //logger.info("***PATH: " + request.getRequestURL());
            // classloader/deployment aware RestasyProviderFactory.  Used to have request specific
            // ResteasyProviderFactory.getInstance()
            ResteasyProviderFactory defaultInstance = ResteasyProviderFactory.getInstance();
            if (defaultInstance instanceof ThreadLocalResteasyProviderFactory) {
                ThreadLocalResteasyProviderFactory.push(providerFactory);
            }
            ResteasyHttpHeaders headers = null;
            ResteasyUriInfo uriInfo = null;
            try {
                headers = ServletUtil.extractHttpHeaders(request);
                uriInfo = ServletUtil.extractUriInfo(request, servletMappingPrefix);
            } catch (Exception e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                // made it warn so that people can filter this.
                LogMessages.LOGGER.failedToParseRequest(e);
                return;
            }

            try (HttpResponse theResponse = responseFactory.createResteasyHttpResponse(response)) {
                HttpRequest in = requestFactory.createResteasyHttpRequest(httpMethod, request, headers, uriInfo, theResponse,
                        response);

                ResteasyContext.pushContext(HttpServletRequest.class, request);
                ResteasyContext.pushContext(HttpServletResponse.class, response);

                ResteasyContext.pushContext(SecurityContext.class, new ServletSecurityContext(request));
                ResteasyContext.pushContext(ServletConfig.class, servletConfig);

                if (handleNotFound) {
                    dispatcher.invoke(in, theResponse);
                } else {
                    ((SynchronousDispatcher) dispatcher).invokePropagateNotFound(in, theResponse);
                }
            } finally {
                ResteasyContext.clearContextData();
            }
        } finally {
            ResteasyProviderFactory defaultInstance = ResteasyProviderFactory.getInstance();
            if (defaultInstance instanceof ThreadLocalResteasyProviderFactory) {
                ThreadLocalResteasyProviderFactory.pop();
            }

        }
    }
}
