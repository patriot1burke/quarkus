package io.quarkus.vertx.http.runtime.security;

import java.util.function.BiFunction;

import javax.enterprise.inject.spi.CDI;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.jboss.logging.Logger;

@Recorder
public class HttpSecurityRecorder {
    private static final Logger LOGGER = Logger.getLogger(HttpSecurityRecorder.class.getName());

    public Handler<RoutingContext> authenticationMechanismHandler() {
        return new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext event) {
                HttpAuthenticator authenticator = CDI.current().select(HttpAuthenticator.class).get();
                //we put the authenticator into the routing context so it can be used by other systems
                event.put(HttpAuthenticator.class.getName(), authenticator);
                event.request().pause();
                authenticator.attemptAuthentication(event).handle(new BiFunction<SecurityIdentity, Throwable, Object>() {
                    @Override
                    public Object apply(SecurityIdentity identity, Throwable throwable) {
                        if (throwable != null) {
                            //auth failed
                            if (throwable instanceof AuthenticationFailedException) {
                                authenticator.sendChallenge(event, new Runnable() {
                                    @Override
                                    public void run() {
                                        LOGGER.warn("**** ending response after challenge");
                                        event.response().end();
                                    }
                                });
                            } else {
                                LOGGER.warn("**** ending response after exception", throwable);
                                event.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
                                event.response().end();
                            }
                            return null;
                        }
                        if (identity != null) {
                            event.setUser(new QuarkusHttpUser(identity));
                        }
                        LOGGER.info("*** auth mech calling NEXT");
                        /*
                        if (!event.request().isEnded()) {
                            event.request().resume();
                        }
                         */
                        event.next();
                        return null;
                    }
                });
            }
        };
    }

}
