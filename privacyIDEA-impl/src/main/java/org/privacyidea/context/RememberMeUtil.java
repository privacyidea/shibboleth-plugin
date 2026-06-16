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

import net.shibboleth.shared.security.DataSealer;
import net.shibboleth.shared.security.DataSealerException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Helpers for the "remember this device" feature.
 * <p>
 * The remembered state is stored in a browser cookie whose value is produced by the IdP's
 * {@link DataSealer} ({@code shibboleth.DataSealer}). The sealer provides authenticated encryption
 * <em>and</em> an embedded expiration timestamp, so the cookie cannot be read, forged or replayed
 * past its expiry without access to the IdP's sealer keys. The bound username is the only payload;
 * the caller is responsible for comparing it against the principal that just authenticated so a
 * stolen cookie cannot bypass the second factor for a different account.
 */
public final class RememberMeUtil
{
    /**
     * Payload format marker: the {@code "<version>|"} prefix prepended to the username before sealing.
     * {@link #unseal} verifies this prefix and strips it via {@code substring} (it does not split on
     * '|'), so usernames that themselves contain '|' survive the round-trip intact.
     */
    private static final String PAYLOAD_PREFIX = "1|";

    private RememberMeUtil() {}

    /**
     * Seal the given username into a cookie value that expires after {@code days} days.
     *
     * @param sealer   the IdP data sealer
     * @param username the username to bind the cookie to
     * @param days     validity in days
     * @return the sealed (encrypted, authenticated, self-expiring) cookie value
     * @throws DataSealerException if sealing fails
     */
    @Nonnull
    public static String seal(@Nonnull DataSealer sealer, @Nonnull String username, int days) throws DataSealerException
    {
        Instant expiration = Instant.now().plus(days, ChronoUnit.DAYS);
        return sealer.wrap(PAYLOAD_PREFIX + username, expiration);
    }

    /**
     * Unseal a cookie value and return the bound username. Returns {@code null} if the value is
     * blank, tampered with, expired, or in an unexpected format — in every "not a valid current
     * token" case the caller simply falls back to a normal challenge.
     *
     * @param sealer      the IdP data sealer
     * @param cookieValue the raw cookie value
     * @return the remembered username, or {@code null} if the cookie is not a valid current token
     */
    @Nullable
    public static String unseal(@Nonnull DataSealer sealer, @Nullable String cookieValue)
    {
        if (StringUtil.isBlank(cookieValue))
        {
            return null;
        }
        try
        {
            String payload = sealer.unwrap(cookieValue);
            if (payload != null && payload.startsWith(PAYLOAD_PREFIX))
            {
                String username = payload.substring(PAYLOAD_PREFIX.length());
                return StringUtil.isNotBlank(username) ? username : null;
            }
        }
        catch (DataSealerException e)
        {
            // Tampered, expired or sealed with a now-removed key: treat as no remember-me cookie.
        }
        return null;
    }
}
