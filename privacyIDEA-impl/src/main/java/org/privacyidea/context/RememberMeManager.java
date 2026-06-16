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
import net.shibboleth.shared.security.DataSealer;
import net.shibboleth.shared.security.DataSealerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Central component for the "remember this device" feature. It holds the configuration once for both the
 * issuing action ({@link org.privacyidea.action.PrivacyIDEAAuthenticator}) and the reading action
 * ({@link org.privacyidea.action.InitializePIContext}), and performs the actual cookie I/O through the
 * IdP's {@link CookieManager} so the cookie's path, {@code Secure} and {@code HttpOnly} flags follow the
 * same conventions as every other IdP cookie. The sealed payload (the username) is produced and consumed
 * by {@link RememberMeUtil}.
 */
public class RememberMeManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RememberMeManager.class);

    private static final int DEFAULT_DAYS = 30;
    private static final int SECONDS_PER_DAY = 86400;

    private boolean rememberMeEnabled = false;
    private int rememberMeDays = DEFAULT_DAYS;
    @Nonnull
    private String rememberMeCookieName = "shib_idp_pidea_rememberme";
    @Nullable
    private DataSealer dataSealer;
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
        if (dataSealer == null)
        {
            LOGGER.warn("Remember-me is enabled but no DataSealer is configured; the feature will be inactive.");
        }
        CookieManager manager = new CookieManager();
        manager.setHttpServletRequestSupplier(httpServletRequestSupplier);
        manager.setHttpServletResponseSupplier(httpServletResponseSupplier);
        manager.setSecure(true);
        manager.setHttpOnly(true);
        // Cookie max-age is an int number of seconds (~68 years max). Compute in long and cap it so a
        // large remember_me_days cannot overflow to a negative/short max-age.
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
     * Issue a remember-me cookie bound to the given username. No-op when the feature is disabled, when no
     * DataSealer is configured, or when the username is blank.
     *
     * @param username the authenticated username to bind the cookie to
     */
    public void issue(@Nullable String username)
    {
        if (!rememberMeEnabled || cookieManager == null || dataSealer == null || StringUtil.isBlank(username))
        {
            return;
        }
        try
        {
            cookieManager.addCookie(rememberMeCookieName, RememberMeUtil.seal(dataSealer, username, rememberMeDays));
            LOGGER.debug("Issued remember-me cookie for '{}', valid {} day(s).", username, rememberMeDays);
        }
        catch (DataSealerException e)
        {
            LOGGER.error("Failed to seal remember-me cookie: {}", e.getMessage());
        }
    }

    /**
     * Check whether the request carries a valid remember-me cookie bound to {@code expectedUsername}. The
     * cookie is only honored when the sealed username matches exactly, so a stolen or copied cookie cannot
     * skip the second factor for another account.
     *
     * @param expectedUsername the username established by the preceding first factor
     * @return {@code true} if a valid, unexpired cookie bound to that exact username is present
     */
    public boolean isRemembered(@Nullable String expectedUsername)
    {
        if (!rememberMeEnabled || cookieManager == null || dataSealer == null || StringUtil.isBlank(expectedUsername))
        {
            return false;
        }
        String cookieValue = cookieManager.getCookieValue(rememberMeCookieName, null);
        return expectedUsername.equals(RememberMeUtil.unseal(dataSealer, cookieValue));
    }

    // Spring bean property setters

    public void setRememberMeEnabled(boolean rememberMeEnabled) {this.rememberMeEnabled = rememberMeEnabled;}

    /**
     * Set the cookie validity in days. Parsed defensively: a blank, non-numeric or non-positive value is
     * ignored (the default of {@value #DEFAULT_DAYS} days is kept) rather than failing flow startup.
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

    public void setDataSealer(@Nullable DataSealer dataSealer) {this.dataSealer = dataSealer;}

    public void setHttpServletRequestSupplier(@Nullable NonnullSupplier<HttpServletRequest> httpServletRequestSupplier)
    {
        this.httpServletRequestSupplier = httpServletRequestSupplier;
    }

    public void setHttpServletResponseSupplier(@Nullable NonnullSupplier<HttpServletResponse> httpServletResponseSupplier)
    {
        this.httpServletResponseSupplier = httpServletResponseSupplier;
    }
}
