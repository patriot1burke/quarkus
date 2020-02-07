package io.quarkus.resteasy.runtime;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.NewCookie;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.plugins.server.servlet.HttpRequestFactory;
import org.jboss.resteasy.plugins.server.servlet.HttpResponseFactory;
import org.jboss.resteasy.plugins.server.servlet.HttpServletResponseWrapper;
import org.jboss.resteasy.plugins.server.servlet.Servlet3AsyncHttpRequest;
import org.jboss.resteasy.plugins.server.servlet.ServletBootstrap;
import org.jboss.resteasy.specimpl.ResteasyHttpHeaders;
import org.jboss.resteasy.specimpl.ResteasyUriInfo;
import org.jboss.resteasy.spi.Dispatcher;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.ResteasyConfiguration;

public class ResteasyServlet extends HttpServlet implements HttpRequestFactory, HttpResponseFactory {
    protected QuarkusServletContainerDispatcher servletContainerDispatcher;
    protected ScheduledExecutorService asyncCancelScheduler = Executors.newScheduledThreadPool(0); // this is to get around TCK tests that call setTimeout in a separate thread which is illegal.
    protected ServletBootstrap bootstrap;
    public static ResteasyServlet QUARKUS_INSTANCE = null;

    public Dispatcher getDispatcher() {
        return servletContainerDispatcher.getDispatcher();
    }

    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        QUARKUS_INSTANCE = this;
        bootstrap = new ServletBootstrap(servletConfig);
    }

    public void preDeploy(QuarkusResteasyDeployment deployment) {
        // need to push context objects for provider and resource setup
        Map<Class<?>, Object> map = ResteasyContext.getContextDataMap();
        map.put(ServletContext.class, getServletConfig().getServletContext());
        map.put(ServletConfig.class, getServletConfig());
        deployment.setDefaultContextObject(ResteasyConfiguration.class, bootstrap);
        deployment.setDefaultContextObject(ServletConfig.class, getServletConfig());
        deployment.setDefaultContextObject(ServletContext.class, getServletConfig().getServletContext());
    }

    public void postDeploy(QuarkusResteasyDeployment deployment, String servletContextPrefix) {
        servletContainerDispatcher = new QuarkusServletContainerDispatcher(getServletConfig());
        servletContainerDispatcher.init(deployment, servletContextPrefix, this, this);
    }

    @Override
    public void destroy() {
        super.destroy();
        servletContainerDispatcher.destroy();
    }

    @Override
    protected void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
            throws ServletException, IOException {
        service(httpServletRequest.getMethod(), httpServletRequest, httpServletResponse);
    }

    public void service(String httpMethod, HttpServletRequest request, HttpServletResponse response) throws IOException {
        servletContainerDispatcher.service(httpMethod, request, response, true);
    }

    public class AsyncHttpRequest extends Servlet3AsyncHttpRequest {
        public AsyncHttpRequest(HttpServletRequest httpServletRequest, HttpServletResponse response,
                ServletContext servletContext, HttpResponse httpResponse, ResteasyHttpHeaders httpHeaders,
                ResteasyUriInfo uriInfo, String s, SynchronousDispatcher synchronousDispatcher) {
            super(httpServletRequest, response, servletContext, httpResponse, httpHeaders, uriInfo, s, synchronousDispatcher);
            this.asyncScheduler = asyncCancelScheduler;
        }
    }

    protected HttpRequest createHttpRequest(String httpMethod, HttpServletRequest httpServletRequest,
            ResteasyHttpHeaders httpHeaders, ResteasyUriInfo uriInfo, HttpResponse httpResponse,
            HttpServletResponse httpServletResponse) {
        return new AsyncHttpRequest(httpServletRequest, httpServletResponse, getServletContext(), httpResponse, httpHeaders,
                uriInfo, httpMethod.toUpperCase(), (SynchronousDispatcher) getDispatcher());
    }

    protected HttpResponse createServletResponse(HttpServletResponse response) {
        return new HttpServletResponseWrapper(response, getDispatcher().getProviderFactory()) {
            @Override
            public void addNewCookie(NewCookie cookie) {
                outputHeaders.add(javax.ws.rs.core.HttpHeaders.SET_COOKIE, cookie);
            }
        };
    }

    public HttpRequest createResteasyHttpRequest(String httpMethod, HttpServletRequest request, ResteasyHttpHeaders headers,
            ResteasyUriInfo uriInfo, HttpResponse theResponse, HttpServletResponse response) {
        return createHttpRequest(httpMethod, request, headers, uriInfo, theResponse, response);
    }

    public HttpResponse createResteasyHttpResponse(HttpServletResponse response) {
        return createServletResponse(response);
    }
}
