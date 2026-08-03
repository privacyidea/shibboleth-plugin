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

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link RememberMeManager}: the Set-Cookie Max-Age parsing (finding A8) and the server
 * capability cache semantics (the /validate/capabilities gating).
 */
public class RememberMeManagerTest
{
    // --- hasNonPositiveMaxAge (A8: a rotated cookie must not be mistaken for a delete) ---

    @Test
    public void maxAgeZeroIsClear()
    {
        assertTrue(RememberMeManager.hasNonPositiveMaxAge("pi_remember_device=; Max-Age=0; Path=/"));
    }

    @Test
    public void negativeMaxAgeIsClear()
    {
        assertTrue(RememberMeManager.hasNonPositiveMaxAge("pi_remember_device=x; Max-Age=-1"));
    }

    @Test
    public void maxAgeIsCaseInsensitive()
    {
        assertTrue(RememberMeManager.hasNonPositiveMaxAge("pi_remember_device=; max-age=0"));
    }

    @Test
    public void leadingZeroMaxAgeIsNotClear()
    {
        // The regression this guards: contains("max-age=0") false-matched values like 03600.
        assertFalse(RememberMeManager.hasNonPositiveMaxAge("pi_remember_device=s:5; Max-Age=03600; Path=/"));
    }

    @Test
    public void positiveMaxAgeIsNotClear()
    {
        assertFalse(RememberMeManager.hasNonPositiveMaxAge("pi_remember_device=s:5; Max-Age=2592000"));
    }

    @Test
    public void noMaxAgeAttributeIsNotClear()
    {
        assertFalse(RememberMeManager.hasNonPositiveMaxAge("pi_remember_device=s:5; Path=/; Secure"));
    }

    // --- capability cache (definitive answers vs the inconclusive/unknown short window) ---

    @Test
    public void unresolvedByDefault()
    {
        RememberMeManager manager = new RememberMeManager();
        assertFalse(manager.isCapabilityResolved());
        assertFalse(manager.isServerCapable());
    }

    @Test
    public void definitiveTrueIsCapableAndResolved()
    {
        RememberMeManager manager = new RememberMeManager();
        manager.cacheServerCapability(Boolean.TRUE);
        assertTrue(manager.isCapabilityResolved());
        assertTrue(manager.isServerCapable());
    }

    @Test
    public void definitiveFalseIsResolvedButNotCapable()
    {
        RememberMeManager manager = new RememberMeManager();
        manager.cacheServerCapability(Boolean.FALSE);
        assertTrue(manager.isCapabilityResolved());
        assertFalse(manager.isServerCapable());
    }

    @Test
    public void inconclusiveProbeIsNotCapableButBrieflySuppressesReprobing()
    {
        RememberMeManager manager = new RememberMeManager();
        manager.cacheServerCapability(null);
        // Fails closed: not capable, so remember-me stays inactive...
        assertFalse(manager.isServerCapable());
        // ...but the recent probe is remembered briefly, so we do not re-probe (and block) every login.
        assertTrue(manager.isCapabilityResolved());
    }

    @Test
    public void inconclusiveProbeDoesNotClobberAnEarlierDefinitiveTrue()
    {
        RememberMeManager manager = new RememberMeManager();
        manager.cacheServerCapability(Boolean.TRUE);
        manager.cacheServerCapability(null);
        // A transient blip must not flip a known-capable server to not-capable.
        assertTrue(manager.isServerCapable());
    }
}
