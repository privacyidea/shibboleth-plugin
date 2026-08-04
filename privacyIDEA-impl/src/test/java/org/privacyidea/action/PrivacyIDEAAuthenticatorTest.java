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
package org.privacyidea.action;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Unit tests for {@link PrivacyIDEAAuthenticator#localPart(String)}, which normalizes an IdP canonical
 * principal to its bare local part so it can be compared against the bare username privacyIDEA returns for
 * a passkey (finding A3: a realm/scope-qualified principal must not falsely mismatch its own passkey).
 */
public class PrivacyIDEAAuthenticatorTest
{
    @Test
    public void bareUsernameUnchanged()
    {
        assertEquals(PrivacyIDEAAuthenticator.localPart("alice"), "alice");
    }

    @Test
    public void stripsAtRealmSuffix()
    {
        assertEquals(PrivacyIDEAAuthenticator.localPart("alice@example.org"), "alice");
    }

    @Test
    public void stripsWindowsDomainPrefix()
    {
        assertEquals(PrivacyIDEAAuthenticator.localPart("EXAMPLE\\alice"), "alice");
    }

    @Test
    public void stripsBothPrefixAndSuffix()
    {
        assertEquals(PrivacyIDEAAuthenticator.localPart("EXAMPLE\\alice@example.org"), "alice");
    }

    @Test
    public void doesNotAlterCase()
    {
        // localPart only trims decorations; case-insensitive comparison is the caller's job.
        assertEquals(PrivacyIDEAAuthenticator.localPart("Alice@Example.org"), "Alice");
    }
}
