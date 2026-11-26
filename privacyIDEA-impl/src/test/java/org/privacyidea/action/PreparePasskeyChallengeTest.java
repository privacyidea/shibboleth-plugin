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

import net.shibboleth.idp.authn.context.AuthenticationContext;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.PIResponse;
import org.privacyidea.PrivacyIDEA;
import org.privacyidea.context.Config;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIFormContext;
import org.privacyidea.context.PIServerConfigContext;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * passkey_ui conditional or immediate: the username form gets a new usernameless passkey challenge on each render.
 * The button mode and a challenge from the passkey button (the browser dialog) stay as before.
 */
public class PreparePasskeyChallengeTest
{
    private PrivacyIDEA privacyIDEA;
    private PIContext piContext;
    private ProfileRequestContext profileRequestContext;
    private PreparePasskeyChallenge action;

    private void setUp(String passkeyUi, boolean disablePasskey) throws Exception
    {
        privacyIDEA = mock(PrivacyIDEA.class);
        profileRequestContext = new ProfileRequestContext();
        AuthenticationContext authenticationContext = profileRequestContext.ensureSubcontext(AuthenticationContext.class);
        piContext = new PIContext(null, "test");
        authenticationContext.addSubcontext(piContext);
        PIFormContext piFormContext = new PIFormContext(null, null, null, false, null, disablePasskey, false);
        piFormContext.setPasskeyUi(passkeyUi);
        authenticationContext.addSubcontext(piFormContext);
        authenticationContext.addSubcontext(new PIServerConfigContext(mock(Config.class)));

        action = new PreparePasskeyChallenge();
        action.setPrivacyIDEA(privacyIDEA);
        action.initialize();
    }

    private static PIResponse challenge(String transactionID)
    {
        PIResponse response = new PIResponse();
        response.passkeyChallenge = "{\"challenge\":\"" + transactionID + "\",\"transaction_id\":\"" + transactionID + "\"}";
        response.transactionID = transactionID;
        return response;
    }

    @Test
    public void conditionalCreatesChallenge() throws Exception
    {
        setUp(PIFormContext.PASSKEY_UI_CONDITIONAL, false);
        when(privacyIDEA.validateInitialize("passkey")).thenReturn(challenge("tx1"));

        action.execute(profileRequestContext);

        assertTrue(piContext.isPasskeyPrepared());
        assertEquals(piContext.getPasskeyTransactionID(), "tx1");
        assertTrue(piContext.getPasskeyChallenge().contains("tx1"));
    }

    @Test
    public void eachRenderGetsNewChallenge() throws Exception
    {
        setUp(PIFormContext.PASSKEY_UI_IMMEDIATE, false);
        when(privacyIDEA.validateInitialize("passkey")).thenReturn(challenge("tx1"), challenge("tx2"));

        action.execute(profileRequestContext);
        action.execute(profileRequestContext);

        assertTrue(piContext.isPasskeyPrepared());
        assertEquals(piContext.getPasskeyTransactionID(), "tx2");
    }

    @Test
    public void buttonModeCreatesNoChallenge() throws Exception
    {
        setUp(PIFormContext.PASSKEY_UI_BUTTON, false);

        action.execute(profileRequestContext);

        verify(privacyIDEA, never()).validateInitialize("passkey");
        assertFalse(piContext.isPasskeyPrepared());
        assertEquals(piContext.getPasskeyChallenge(), "");
    }

    @Test
    public void disabledPasskeyCreatesNoChallenge() throws Exception
    {
        setUp(PIFormContext.PASSKEY_UI_CONDITIONAL, true);

        action.execute(profileRequestContext);

        verify(privacyIDEA, never()).validateInitialize("passkey");
        assertFalse(piContext.isPasskeyPrepared());
    }

    @Test
    public void challengeForTheDialogIsKept() throws Exception
    {
        // The passkey button reached the server without the page script. The page opens the dialog for this challenge.
        setUp(PIFormContext.PASSKEY_UI_CONDITIONAL, false);
        piContext.setPasskeyChallenge("{\"challenge\":\"dialog\"}");
        piContext.setPasskeyTransactionID("txDialog");

        action.execute(profileRequestContext);

        verify(privacyIDEA, never()).validateInitialize("passkey");
        assertFalse(piContext.isPasskeyPrepared());
        assertEquals(piContext.getPasskeyTransactionID(), "txDialog");
    }

    @Test
    public void failedInitializeLeavesNoChallenge() throws Exception
    {
        setUp(PIFormContext.PASSKEY_UI_CONDITIONAL, false);
        piContext.setPasskeyChallenge("{\"challenge\":\"old\"}");
        piContext.setPasskeyTransactionID("txOld");
        piContext.setPasskeyPrepared(true);
        when(privacyIDEA.validateInitialize("passkey")).thenReturn(null);

        action.execute(profileRequestContext);

        assertFalse(piContext.isPasskeyPrepared());
        assertEquals(piContext.getPasskeyChallenge(), "");
        assertNull(piContext.getPasskeyTransactionID());
    }
}
