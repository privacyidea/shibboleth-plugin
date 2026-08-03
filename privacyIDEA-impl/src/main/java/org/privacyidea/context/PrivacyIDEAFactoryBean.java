/*
 * Copyright 2026 NetKnights GmbH - nils.behlen@netknights.it
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.privacyidea.context;

import org.privacyidea.IPILogger;
import org.privacyidea.PrivacyIDEA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

import javax.annotation.Nullable;

/**
 * Builds the shared {@link PrivacyIDEA} java-client once per flow context and hands the single instance to
 * every action, instead of rebuilding it per request.
 * <p>
 * The client wraps an OkHttp client plus a thread pool and a scheduler; OkHttp is designed to be shared, and
 * the client holds only static configuration (server URL, realm, service account) plus a cached
 * service-account JWT — all safe to share across requests. It is a Spring {@link FactoryBean}, so
 * {@code p:privacyIDEA-ref="privacyIDEAClient"} injects the {@link PrivacyIDEA} object itself. The bean is
 * built in {@link #initialize()} and closed in {@link #destroy()} (the beans file's default init/destroy
 * methods), so the executors are shut down on context teardown / redeploy.
 * <p>
 * Because the client is shared, it cannot borrow a per-request action as its {@link IPILogger}; this bean is
 * its own slf4j-backed logger, gated on the configured {@code debug} flag. The tradeoff is that the client's
 * own log lines no longer carry a per-request profile-action prefix.
 */
public class PrivacyIDEAFactoryBean implements FactoryBean<PrivacyIDEA>, IPILogger
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PrivacyIDEAFactoryBean.class);

    private String serverURL = "https://localhost";
    private boolean verifySSL = true;
    @Nullable
    private String realm;
    @Nullable
    private String serviceName;
    @Nullable
    private String servicePass;
    @Nullable
    private String serviceRealm;
    /** HTTP timeout in ms for all privacyIDEA calls; defaults to the java-client default (10s). */
    private int httpTimeoutMs = 10000;
    private boolean debug = false;

    @Nullable
    private PrivacyIDEA privacyIDEA;

    /**
     * Spring init-method (via the beans file's {@code default-init-method}). Builds the shared client from
     * the configured properties.
     */
    public void initialize()
    {
        String userAgent = "privacyIDEA-Shibboleth/" + org.privacyidea.Version.getVersion()
                + " ShibbolethIdP/" + net.shibboleth.idp.Version.getVersion();
        privacyIDEA = PrivacyIDEA.newBuilder(serverURL, userAgent)
                                 .verifySSL(verifySSL)
                                 .realm(realm)
                                 .serviceAccount(serviceName, servicePass)
                                 .serviceRealm(serviceRealm)
                                 .httpTimeoutMs(httpTimeoutMs)
                                 .logger(this)
                                 .build();
    }

    /**
     * Spring destroy-method (via the beans file's {@code default-destroy-method}). Shuts down the client's
     * executors on context teardown / redeploy.
     */
    public void destroy()
    {
        if (privacyIDEA != null)
        {
            try
            {
                privacyIDEA.close();
            }
            catch (Exception e)
            {
                LOGGER.debug("Error closing the shared privacyIDEA client", e);
            }
        }
    }

    @Override
    public PrivacyIDEA getObject()
    {
        if (privacyIDEA == null)
        {
            // Fail fast: initialize() runs before getObject() (Spring init-method), so a null here means
            // the client was never built — surface it at context startup instead of injecting null and
            // NPE-ing at request time.
            throw new IllegalStateException("privacyIDEA client was not initialized; check the server configuration.");
        }
        return privacyIDEA;
    }

    @Override
    public Class<?> getObjectType()
    {
        return PrivacyIDEA.class;
    }

    @Override
    public boolean isSingleton()
    {
        return true;
    }

    // slf4j-backed IPILogger for the shared client (no per-request prefix); debug-gated.

    @Override
    public void log(String message)
    {
        if (debug)
        {
            LOGGER.info("{}", message);
        }
    }

    @Override
    public void error(String message)
    {
        if (debug)
        {
            LOGGER.error("{}", message);
        }
    }

    @Override
    public void log(Throwable t)
    {
        if (debug)
        {
            LOGGER.info("privacyIDEA client:", t);
        }
    }

    @Override
    public void error(Throwable t)
    {
        if (debug)
        {
            LOGGER.error("privacyIDEA client:", t);
        }
    }

    // Spring bean property setters

    public void setServerURL(String serverURL) {this.serverURL = serverURL;}

    public void setVerifySSL(boolean verifySSL) {this.verifySSL = verifySSL;}

    public void setRealm(@Nullable String realm) {this.realm = realm;}

    public void setServiceName(@Nullable String serviceName) {this.serviceName = serviceName;}

    public void setServicePass(@Nullable String servicePass) {this.servicePass = servicePass;}

    public void setServiceRealm(@Nullable String serviceRealm) {this.serviceRealm = serviceRealm;}

    /**
     * Set the HTTP timeout (milliseconds) for privacyIDEA calls. Parsed defensively: a blank, non-numeric
     * or non-positive value is ignored (the default of 10000 ms is kept) rather than failing flow startup.
     *
     * @param httpTimeoutMs the configured value (digits only)
     */
    public void setHttpTimeoutMs(@Nullable String httpTimeoutMs)
    {
        if (StringUtil.isBlank(httpTimeoutMs))
        {
            return;
        }
        try
        {
            int parsed = Integer.parseInt(httpTimeoutMs.trim());
            if (parsed > 0)
            {
                this.httpTimeoutMs = parsed;
            }
            else
            {
                LOGGER.warn("Config option \"http_timeout_ms\": must be a positive number. Using default {}.", this.httpTimeoutMs);
            }
        }
        catch (NumberFormatException e)
        {
            LOGGER.warn("Config option \"http_timeout_ms\": Wrong format. Only digits allowed. Using default {}.", this.httpTimeoutMs);
        }
    }

    public void setDebug(boolean debug) {this.debug = debug;}
}
