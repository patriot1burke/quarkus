package io.quarkus.resteasy.runtime;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.ws.rs.core.NewCookie;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.plugins.server.servlet.FilterBootstrap;
import org.jboss.resteasy.plugins.server.servlet.HttpRequestFactory;
import org.jboss.resteasy.plugins.server.servlet.HttpResponseFactory;
import org.jboss.resteasy.plugins.server.servlet.Servlet3AsyncHttpRequest;
import org.jboss.resteasy.specimpl.ResteasyHttpHeaders;
import org.jboss.resteasy.specimpl.ResteasyUriInfo;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.ResteasyConfiguration;

/**
 * A filter that will be mapped to the default servlet. At first content will attempt to be served from the
 * default servlet, and if it fails then a REST response will be attempted.
 *
 * This is initially instantiated by Quarkus Undertow extension. Resteasy extension follow
 * up by looking for static QUARKUS_INSTANCE to finish up deployment.
 */
public class ResteasyFilter implements Filter, HttpRequestFactory, HttpResponseFactory {
    ScheduledExecutorService asyncCancelScheduler = Executors.newScheduledThreadPool(0); // this is to get around TCK tests that call setTimeout in a separate thread which is illegal.
    protected QuarkusServletContainerDispatcher servletContainerDispatcher;
    protected ServletContext servletContext;
    protected FilterConfig filterConfig;
    protected FilterBootstrap bootstrap;

    public static ResteasyFilter QUARKUS_INSTANCE = null;

    public void init(FilterConfig config) throws ServletException {
        servletContext = config.getServletContext();
        filterConfig = config;
        QUARKUS_INSTANCE = this;
        bootstrap = new FilterBootstrap(config);
    }

    public void preDeploy(QuarkusResteasyDeployment deployment) {
        // need to push context objects for provider and resource setup
        Map<Class<?>, Object> map = ResteasyContext.getContextDataMap();
        map.put(ServletContext.class, servletContext);
        map.put(FilterConfig.class, filterConfig);
        deployment.setDefaultContextObject(ResteasyConfiguration.class, bootstrap);
        deployment.setDefaultContextObject(FilterConfig.class, filterConfig);
        deployment.setDefaultContextObject(ServletContext.class, filterConfig.getServletContext());
    }

    public void postDeploy(QuarkusResteasyDeployment deployment, String servletContextPrefix) {
        servletContainerDispatcher = new QuarkusServletContainerDispatcher();
        servletContainerDispatcher.init(deployment, servletContextPrefix, this, this);
    }

    public class AsyncHttpRequest extends Servlet3AsyncHttpRequest {
        public AsyncHttpRequest(HttpServletRequest httpServletRequest, HttpServletResponse response,
                ServletContext servletContext, HttpResponse httpResponse, ResteasyHttpHeaders httpHeaders,
                ResteasyUriInfo uriInfo, String s, SynchronousDispatcher synchronousDispatcher) {
            super(httpServletRequest, response, servletContext, httpResponse, httpHeaders, uriInfo, s, synchronousDispatcher);
            this.asyncScheduler = asyncCancelScheduler;
        }
    }

    @Override
    public HttpRequest createResteasyHttpRequest(String httpMethod, HttpServletRequest httpServletRequest,
            ResteasyHttpHeaders httpHeaders, ResteasyUriInfo uriInfo, HttpResponse httpResponse,
            HttpServletResponse httpServletResponse) {
        return new AsyncHttpRequest(httpServletRequest, httpServletResponse, servletContext, httpResponse, httpHeaders, uriInfo,
                httpMethod, (SynchronousDispatcher) servletContainerDispatcher.getDispatcher());
    }

    @Override
    public HttpResponse createResteasyHttpResponse(HttpServletResponse response) {
        return new org.jboss.resteasy.plugins.server.servlet.HttpServletResponseWrapper(response,
                servletContainerDispatcher.getDispatcher().getProviderFactory()) {
            @Override
            public void addNewCookie(NewCookie cookie) {
                outputHeaders.add(javax.ws.rs.core.HttpHeaders.SET_COOKIE, cookie);
            }
        };
    }

    public void destroy() {
        servletContainerDispatcher.destroy();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        if (request.getMethod().equals("GET") || request.getMethod().equals("HEAD") || isCORSPreflightRequest(request)) {
            //we only serve get requests from the default servlet and CORS preflight requests
            filterChain.doFilter(servletRequest, new ResteasyResponseWrapper(response, request));
        } else {
            servletContainerDispatcher.service(request.getMethod(), request, response, true);
        }
    }

    private boolean isCORSPreflightRequest(HttpServletRequest request) {
        return request.getMethod().equals("OPTIONS")
                && request.getHeader("Origin") != null
                && request.getHeader("Access-Control-Request-Method") != null
                && request.getHeader("Access-Control-Request-Headers") != null;
    }

    private class ResteasyResponseWrapper extends HttpServletResponseWrapper {

        final HttpServletRequest request;
        final HttpServletResponse response;

        public ResteasyResponseWrapper(HttpServletResponse response, HttpServletRequest request) {
            super(response);
            this.request = request;
            this.response = response;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            if (sc == 404 || sc == 403) {

                servletContainerDispatcher.service(request.getMethod(), request, response, true);
            } else {
                super.sendError(sc, msg);
            }
        }

        @Override
        public void sendError(int sc) throws IOException {
            if (sc == 404 || sc == 403) {
                servletContainerDispatcher.service(request.getMethod(), request, response, true);
            } else {
                super.sendError(sc);
            }
        }
    }
}
