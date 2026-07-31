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

    private static final int DEFAULT_DAYS = 30;
    private static final int SECONDS_PER_DAY = 86400;
    private static final String HEADER_API_KEY = "X-API-Key";
    private static final String HEADER_COOKIE = "Cookie";

    private boolean rememberMeEnabled = false;
    private int rememberMeDays = DEFAULT_DAYS;
    @Nonnull
    private String rememberMeCookieName = "pi_remember_device";
    @Nullable
    private String apiKey;
    @Nullable
    private NonnullSupplier<HttpServletRequest> httpServletRequestSupplier;
    @Nullable
    private NonnullSupplier<HttpServletResponse> httpServletResponseSupplier;

    /** Built in {@link #initialize()} when the feature is enabled; stays {@code null} while disabled. */
    @Nullable
    private CookieManager cookieManager;

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
        CookieManager manager = new CookieManager();
        manager.setHttpServletRequestSupplier(httpServletRequestSupplier);
        manager.setHttpServletResponseSupplier(httpServletResponseSupplier);
        manager.setSecure(true);
        manager.setHttpOnly(true);
        // Cookie max-age is an int number of seconds (~68 years max). Compute in long and cap it so a
        // large remember_me_days cannot overflow to a negative/short max-age. privacyIDEA remains the
        // authority on real expiry; if it expires/clears the session, relayResponse() clears our cookie.
        manager.setMaxAge((int) Math.min((long) rememberMeDays * SECONDS_PER_DAY, Integer.MAX_VALUE));
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
     * @return the raw stored cookie value ({@code <series>:<counter>}) for the current request, or
     * {@code null} if there is no cookie / the feature is disabled.
     */
    @Nullable
    public String readCookie()
    {
        return cookieManager == null ? null : cookieManager.getCookieValue(rememberMeCookieName, null);
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
            headers.put(HEADER_COOKIE, rememberMeCookieName + "=" + stored);
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
        String prefix = rememberMeCookieName + "=";
        for (String header : setCookies)
        {
            if (header == null || !header.startsWith(prefix))
            {
                continue;
            }
            String afterName = header.substring(prefix.length());
            int semicolon = afterName.indexOf(';');
            String value = (semicolon >= 0 ? afterName.substring(0, semicolon) : afterName).trim();
            boolean cleared = value.isEmpty() || header.toLowerCase().contains("max-age=0");
            if (cleared)
            {
                clearCookie();
                LOGGER.info("Remember-device: privacyIDEA cleared the cookie (expired, or series reset after a counter mismatch); removing it from the browser.");
            }
            else
            {
                cookieManager.addCookie(rememberMeCookieName, value);
                int colon = value.lastIndexOf(':');
                String counter = colon >= 0 ? value.substring(colon + 1) : "?";
                LOGGER.info("Remember-device: stored {} cookie (counter {}).",
                            "1".equals(counter) ? "newly issued" : "rotated", counter);
            }
            return;
        }
    }

    /**
     * Remove the IdP-domain remember-me cookie from the browser. No-op when disabled.
     */
    public void clearCookie()
    {
        if (cookieManager != null)
        {
            cookieManager.unsetCookie(rememberMeCookieName);
        }
    }

    // Spring bean property setters

    public void setRememberMeEnabled(boolean rememberMeEnabled) {this.rememberMeEnabled = rememberMeEnabled;}

    /**
     * Set the IdP-domain cookie validity in days. Parsed defensively: a blank, non-numeric or
     * non-positive value is ignored (the default of {@value #DEFAULT_DAYS} days is kept) rather than
     * failing flow startup.
     *
     * @param rememberMeDays the configured value (digits only)
     */
    public void setRememberMeDays(@Nullable String rememberMeDays)
    {
        if (StringUtil.isBlank(rememberMeDays))
        {
            return;
        }
        try
        {
            int parsed = Integer.parseInt(rememberMeDays.trim());
            if (parsed > 0)
            {
                this.rememberMeDays = parsed;
            }
            else
            {
                LOGGER.warn("Config option \"remember_me_days\": must be a positive number. Using default {}.", DEFAULT_DAYS);
            }
        }
        catch (NumberFormatException e)
        {
            LOGGER.warn("Config option \"remember_me_days\": Wrong format. Only digits allowed. Using default {}.", DEFAULT_DAYS);
        }
    }

    public void setRememberMeCookieName(@Nonnull String rememberMeCookieName) {this.rememberMeCookieName = rememberMeCookieName;}

    public void setApiKey(@Nullable String apiKey) {this.apiKey = apiKey;}

    public void setHttpServletRequestSupplier(@Nullable NonnullSupplier<HttpServletRequest> httpServletRequestSupplier)
    {
        this.httpServletRequestSupplier = httpServletRequestSupplier;
    }

    public void setHttpServletResponseSupplier(@Nullable NonnullSupplier<HttpServletResponse> httpServletResponseSupplier)
    {
        this.httpServletResponseSupplier = httpServletResponseSupplier;
    }
}
