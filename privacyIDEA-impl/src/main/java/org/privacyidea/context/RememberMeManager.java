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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.shibboleth.shared.component.ComponentInitializationException;
import net.shibboleth.shared.net.CookieManager;
import net.shibboleth.shared.primitive.NonnullSupplier;
import org.privacyidea.PIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Central component for the "remember this device" feature.
 * <p>
 * The rotating persistent-session token is owned by privacyIDEA (see the {@code /validate/check}
 * {@code request_persistent_cookie} / {@code Set-Cookie: pi_remember_device=<series>:<counter>}
 * contract). This plugin is only the transport: the browser talks to the IdP, not to privacyIDEA, so
 * we keep an IdP-domain cookie of the same name that mirrors privacyIDEA's rotating value. On each
 * {@code /validate/check} we send the stored value up as a {@code Cookie} header (plus the client's
 * {@code X-API-Key}), and we write privacyIDEA's returned {@code Set-Cookie} value back down.
 * <p>
 * This bean holds the config once and is shared by {@link org.privacyidea.action.InitializePIContext}
 * and {@link org.privacyidea.action.PrivacyIDEAAuthenticator}. It performs the browser-facing cookie
 * I/O through the IdP's {@link CookieManager} so path / {@code Secure} / {@code HttpOnly} follow IdP
 * conventions.
 */
public class RememberMeManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RememberMeManager.class);

    private static final String HEADER_API_KEY = "X-API-Key";
    private static final String HEADER_COOKIE = "Cookie";
    /** privacyIDEA's fixed remember-device cookie name (matches its Set-Cookie and the Cookie we send up). */
    private static final String COOKIE_NAME = "pi_remember_device";
    /** Max-age used when privacyIDEA's Set-Cookie carries no Max-Age: a session cookie (browser default). */
    private static final int SESSION_COOKIE = -1;

    private boolean rememberMeEnabled = false;
    @Nullable
    private String apiKey;
    @Nullable
    private NonnullSupplier<HttpServletRequest> httpServletRequestSupplier;
    @Nullable
    private NonnullSupplier<HttpServletResponse> httpServletResponseSupplier;

    /** Built in {@link #initialize()} when the feature is enabled; stays {@code null} while disabled. */
    @Nullable
    private MaxAgeCookieManager cookieManager;

    /** How long a definitive {@code /validate/capabilities} answer is trusted before it is re-probed. */
    private static final long CAPABILITY_TTL_MILLIS = 15 * 60 * 1000L;

    /**
     * How long an <em>inconclusive</em> probe (server unreachable / too old) suppresses re-probing. Short,
     * so the feature recovers quickly once the server is reachable, but long enough that a down/old server
     * is not re-probed on every single login (each probe blocks the login form on the HTTP timeout).
     */
    private static final long CAPABILITY_UNKNOWN_TTL_MILLIS = 2 * 60 * 1000L;

    /**
     * Cached client-level answer to {@code GET /validate/capabilities} ({@code remember_device}). This bean
     * is a singleton, so the cache lives for the JVM. {@code null} = not yet resolved; a definitive
     * {@code TRUE}/{@code FALSE} from the server is cached with a {@value #CAPABILITY_TTL_MILLIS}ms TTL (see
     * {@link #capabilityResolvedAt}), but an inconclusive probe (server unreachable / too old) is
     * <em>not</em> — so one transient failure cannot disable the feature. The TTL lets a server-side policy
     * change (e.g. the {@code remember_device} policy being added) be picked up without an IdP restart.
     */
    @Nullable
    private volatile Boolean serverCapable;

    /** {@code System.currentTimeMillis()} when {@link #serverCapable} was last set to a definitive answer. */
    private volatile long capabilityResolvedAt;

    /** {@code System.currentTimeMillis()} of the most recent capability probe, whatever its outcome. */
    private volatile long capabilityProbedAt;

    /**
     * Spring init-method (invoked via the bean file's {@code default-init-method}). When the feature is
     * enabled it builds and initializes the backing {@link CookieManager}; when disabled it is a no-op,
     * so a disabled flow never needs the servlet suppliers.
     *
     * @throws ComponentInitializationException if the CookieManager cannot be initialized
     */
    public void initialize() throws ComponentInitializationException
    {
        if (!rememberMeEnabled)
        {
            return;
        }
        if (StringUtil.isBlank(apiKey))
        {
            LOGGER.warn("Remember-me is enabled but no privacyIDEA API key is configured; the feature will be inactive.");
        }
        // Expiry is entirely server-driven: each stored cookie is written with the Max-Age privacyIDEA
        // sends in its Set-Cookie (see relayResponse), so no local max-age is configured here.
        MaxAgeCookieManager manager = new MaxAgeCookieManager();
        manager.setHttpServletRequestSupplier(httpServletRequestSupplier);
        manager.setHttpServletResponseSupplier(httpServletResponseSupplier);
        manager.setSecure(true);
        manager.setHttpOnly(true);
        manager.initialize();
        cookieManager = manager;
    }

    /**
     * @return whether the "remember this device" feature is enabled.
     */
    public boolean isEnabled()
    {
        return rememberMeEnabled;
    }

    /**
     * @return whether the feature is enabled <em>and</em> usable (initialized cookie manager and an
     * API key present). Only when this is {@code true} is it worth sending remember-me data.
     */
    public boolean isConfigured()
    {
        return rememberMeEnabled && cookieManager != null && StringUtil.isNotBlank(apiKey);
    }

    /**
     * @return whether a definitive server-capability answer is cached <em>and</em> still within its TTL, so
     * the caller can skip the {@code /validate/capabilities} probe. Returns {@code false} once the cached
     * answer has aged past {@value #CAPABILITY_TTL_MILLIS}ms, triggering a re-probe that picks up a
     * server-side policy change without an IdP restart.
     */
    public boolean isCapabilityResolved()
    {
        long now = System.currentTimeMillis();
        if (serverCapable != null && (now - capabilityResolvedAt) < CAPABILITY_TTL_MILLIS)
        {
            return true;
        }
        // An inconclusive probe is remembered only briefly (fails closed via isServerCapable()), so a
        // down/old server is retried soon but does not block every login on the probe's HTTP timeout.
        return capabilityProbedAt > 0 && (now - capabilityProbedAt) < CAPABILITY_UNKNOWN_TTL_MILLIS;
    }

    /**
     * @return whether privacyIDEA has advertised the {@code remember_device} capability for this client.
     * {@code false} until resolved to a definitive {@code true}, so gating on this fails closed.
     */
    public boolean isServerCapable()
    {
        return Boolean.TRUE.equals(serverCapable);
    }

    /**
     * Cache a {@code /validate/capabilities} result and stamp it for the TTL. Only definitive answers are
     * stored; a {@code null} (inconclusive probe) is ignored so it is retried on the next login rather than
     * latching the feature off — and it leaves any prior answer (and its timestamp) untouched, so a stale
     * value keeps being re-probed until the server responds definitively again.
     *
     * @param capability the server's answer, or {@code null} if it could not be determined
     */
    public void cacheServerCapability(@Nullable Boolean capability)
    {
        long now = System.currentTimeMillis();
        capabilityProbedAt = now;
        if (capability != null)
        {
            serverCapable = capability;
            capabilityResolvedAt = now;
        }
    }

    /**
     * @return the raw stored cookie value ({@code <series>:<counter>}) for the current request, or
     * {@code null} if there is no cookie / the feature is disabled.
     */
    @Nullable
    public String readCookie()
    {
        return cookieManager == null ? null : cookieManager.getCookieValue(COOKIE_NAME, null);
    }

    /**
     * Add the {@code X-API-Key} for a {@code /validate/check} issuance request. Call this only when the
     * user opted in (issuance): the key identifies the client so privacyIDEA can issue the cookie. On a
     * plain login the key is <em>not</em> sent, so the call stays on the anonymous/legacy path and a
     * misconfigured or revoked key can never turn an ordinary login into an {@code HTTP 401}. No cookie
     * is sent — {@code /validate/check} does not consume it; recognition is a separate endpoint.
     *
     * @param headers the mutable header map the java-client will send
     */
    public void addApiKey(@Nonnull Map<String, String> headers)
    {
        if (isConfigured())
        {
            headers.put(HEADER_API_KEY, apiKey);
        }
    }

    /**
     * Add the request data for the {@code /validate/remember_device} recognition call: the
     * {@code X-API-Key} and the stored {@code pi_remember_device} cookie. No-op when the feature is not
     * usable or no cookie is stored (in which case the caller should not make the recognition call).
     *
     * @param headers the mutable header map the java-client will send
     */
    public void addRecognitionData(@Nonnull Map<String, String> headers)
    {
        if (!isConfigured())
        {
            return;
        }
        String stored = readCookie();
        if (StringUtil.isNotBlank(stored))
        {
            headers.put(HEADER_API_KEY, apiKey);
            headers.put(HEADER_COOKIE, COOKIE_NAME + "=" + stored);
        }
    }

    /**
     * Apply privacyIDEA's {@code Set-Cookie} response to the IdP-domain cookie: store a rotated value,
     * or clear the cookie when privacyIDEA cleared it (blank value or {@code Max-Age=0} — the hallmark
     * of expiry or theft-triggered series deletion). No-op when the feature is not usable or the
     * response carried no {@code pi_remember_device} cookie.
     *
     * @param piResponse the response from the java-client (its {@code setCookieHeaders})
     */
    public void relayResponse(@Nullable PIResponse piResponse)
    {
        if (!isConfigured() || piResponse == null)
        {
            return;
        }
        List<String> setCookies = piResponse.setCookieHeaders;
        if (setCookies == null)
        {
            return;
        }
        String prefix = COOKIE_NAME + "=";
        for (String header : setCookies)
        {
            if (header == null || !header.startsWith(prefix))
            {
                continue;
            }
            String afterName = header.substring(prefix.length());
            int semicolon = afterName.indexOf(';');
            String value = (semicolon >= 0 ? afterName.substring(0, semicolon) : afterName).trim();
            Integer maxAge = parseMaxAge(header);
            // A blank value or Max-Age<=0 is privacyIDEA clearing the cookie (expiry / theft-triggered
            // series deletion). Otherwise store the rotated value with privacyIDEA's own Max-Age, so the
            // browser cookie expires exactly per the server-side policy (no local day setting).
            boolean cleared = value.isEmpty() || (maxAge != null && maxAge <= 0);
            if (cleared)
            {
                clearCookie();
                LOGGER.info("Remember-device: privacyIDEA did not recognise the cookie; removing it from the browser.");
            }
            else
            {
                cookieManager.addCookie(COOKIE_NAME, value, maxAge != null ? maxAge : SESSION_COOKIE);
                int colon = value.lastIndexOf(':');
                String counter = colon >= 0 ? value.substring(colon + 1) : "?";
                LOGGER.info("Remember-device: stored {} cookie (counter {}).",
                            "1".equals(counter) ? "newly issued" : "rotated", counter);
            }
            return;
        }
    }

    /**
     * Parse the {@code Max-Age} (seconds) from a {@code Set-Cookie} header. Parses the attribute token
     * rather than a substring match, so a valid value that merely starts with a zero digit (e.g.
     * {@code Max-Age=03600}) is read correctly.
     *
     * @param header the raw {@code Set-Cookie} header value
     * @return the Max-Age in seconds, or {@code null} if the attribute is absent or non-numeric
     */
    // package-private for unit testing
    @Nullable
    static Integer parseMaxAge(@Nonnull String header)
    {
        for (String attribute : header.split(";"))
        {
            String token = attribute.trim();
            if (token.regionMatches(true, 0, "Max-Age=", 0, "Max-Age=".length()))
            {
                String maxAge = token.substring("Max-Age=".length()).trim();
                try
                {
                    return Integer.parseInt(maxAge);
                }
                catch (NumberFormatException e)
                {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Remove the IdP-domain remember-me cookie from the browser. No-op when disabled.
     */
    public void clearCookie()
    {
        if (cookieManager != null)
        {
            cookieManager.unsetCookie(COOKIE_NAME);
        }
    }

    // Spring bean property setters

    public void setRememberMeEnabled(boolean rememberMeEnabled) {this.rememberMeEnabled = rememberMeEnabled;}

    public void setApiKey(@Nullable String apiKey) {this.apiKey = apiKey;}

    public void setHttpServletRequestSupplier(@Nullable NonnullSupplier<HttpServletRequest> httpServletRequestSupplier)
    {
        this.httpServletRequestSupplier = httpServletRequestSupplier;
    }

    public void setHttpServletResponseSupplier(@Nullable NonnullSupplier<HttpServletResponse> httpServletResponseSupplier)
    {
        this.httpServletResponseSupplier = httpServletResponseSupplier;
    }

    /**
     * {@link CookieManager} whose per-call, max-age-carrying {@code addCookie} is exposed (it is protected
     * in the base class), so the browser cookie can be written with the {@code Max-Age} privacyIDEA sends
     * rather than a fixed local value — keeping the base class's path / Secure / HttpOnly handling.
     */
    private static final class MaxAgeCookieManager extends CookieManager
    {
        @Override
        public void addCookie(String name, String value, int maxAgeSeconds)
        {
            super.addCookie(name, value, maxAgeSeconds);
        }
    }
}
