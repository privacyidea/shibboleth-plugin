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

import jakarta.servlet.http.HttpServletRequest;
import net.shibboleth.idp.authn.context.AuthenticationContext;
import org.opensaml.profile.context.EventContext;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.AuthenticationStatus;
import org.privacyidea.PIError;
import org.privacyidea.PIResponse;
import org.privacyidea.PrivacyIDEA;
import org.privacyidea.context.Config;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIFormContext;
import org.privacyidea.context.PIServerConfigContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * passkey_ui conditional or immediate: a failed passkey from the username form shows the username form again, because
 * the OTP page needs a user that the passkey did not give. A passkey from the button keeps the behavior before.
 */
public class PrivacyIDEAAuthenticatorPasskeyUiTest
{
    private static final String ORIGIN = "https://idp.example.org";
    private static final String CHALLENGE = "{\"challenge\":\"abc\",\"transaction_id\":\"tx1\"}";

    private PrivacyIDEA privacyIDEA;
    private HttpServletRequest request;
    private ProfileRequestContext profileRequestContext;
    private PIContext piContext;
    private PrivacyIDEAAuthenticator authenticator;

    @BeforeMethod
    public void setUp() throws Exception
    {
        privacyIDEA = mock(PrivacyIDEA.class);
        Config config = mock(Config.class);
        when(config.getAuthenticationFlow()).thenReturn("default");

        request = mock(HttpServletRequest.class);
        when(request.getHeader("Origin")).thenReturn(ORIGIN);

        profileRequestContext = new ProfileRequestContext();
        AuthenticationContext authenticationContext = profileRequestContext.ensureSubcontext(AuthenticationContext.class);
        // The username form of a usernameless login: no user yet.
        piContext = new PIContext(null, "test");
        PIFormContext piFormContext = new PIFormContext(null, null, null, false, null, false, false);
        piFormContext.setPasskeyUi(PIFormContext.PASSKEY_UI_CONDITIONAL);
        authenticationContext.addSubcontext(piContext);
        authenticationContext.addSubcontext(piFormContext);
        authenticationContext.addSubcontext(new PIServerConfigContext(config));

        authenticator = new PrivacyIDEAAuthenticator();
        authenticator.setPrivacyIDEA(privacyIDEA);
        authenticator.setHttpServletRequestSupplier(() -> request);
        authenticator.initialize();
    }

    private String executeAndGetEvent()
    {
        authenticator.execute(profileRequestContext);
        EventContext eventContext = profileRequestContext.getSubcontext(EventContext.class);
        assertNotNull(eventContext);
        return String.valueOf(eventContext.getEvent());
    }

    /** The browser returned a passkey assertion for the challenge with the transaction tx1. */
    private void submitPasskey(boolean prepared, PIResponse answer)
    {
        piContext.setPasskeyChallenge(CHALLENGE);
        piContext.setPasskeyTransactionID("tx1");
        piContext.setPasskeyPrepared(prepared);
        when(request.getParameter("passkeyChallenge")).thenReturn(CHALLENGE);
        when(request.getParameter("passkeySignResponse")).thenReturn("{\"credential_id\":\"c1\"}");
        when(privacyIDEA.validateCheckPasskey(eq("tx1"), anyString(), eq(ORIGIN), anyMap(), anyMap())).thenReturn(answer);
    }

    private static PIResponse reject()
    {
        PIResponse response = new PIResponse();
        response.authentication = AuthenticationStatus.REJECT;
        return response;
    }

    @Test
    public void rejectedPreparedPasskeyShowsUsernameFormAgain()
    {
        submitPasskey(true, reject());

        assertEquals(executeAndGetEvent(), "reloadUsernameForm");
        assertEquals(piContext.getFormErrorMessage(), "Passkey authentication rejected!");
        // The next render creates a new challenge.
        assertEquals(piContext.getPasskeyChallenge(), "");
        assertNull(piContext.getPasskeyTransactionID());
        assertFalse(piContext.isPasskeyPrepared());
    }

    @Test
    public void preparedPasskeyWithServerErrorShowsUsernameFormAgain()
    {
        PIResponse error = new PIResponse();
        error.error = new PIError(905, "ERR905: challenge expired");
        submitPasskey(true, error);

        assertEquals(executeAndGetEvent(), "reloadUsernameForm");
        assertEquals(piContext.getFormErrorMessage(), "ERR905: challenge expired");
        assertEquals(piContext.getPasskeyChallenge(), "");
    }

    @Test
    public void rejectedPasskeyFromButtonKeepsBehavior()
    {
        submitPasskey(false, reject());

        assertEquals(executeAndGetEvent(), "reload");
        assertEquals(piContext.getMode(), "otp");
    }

    @Test
    public void buttonWithoutScriptGetsChallengeForDialog()
    {
        when(request.getParameter("passkeyLoginRequested")).thenReturn("1");
        when(request.getParameterMap()).thenReturn(Map.of("_eventId_passkey", new String[]{"passkey"}));
        PIResponse init = new PIResponse();
        init.passkeyChallenge = CHALLENGE;
        init.transactionID = "tx2";
        when(privacyIDEA.validateInitialize("passkey")).thenReturn(init);
        piContext.setPasskeyPrepared(true);

        assertEquals(executeAndGetEvent(), "reloadUsernameForm");
        assertEquals(piContext.getPasskeyTransactionID(), "tx2");
        // Not prepared: the page opens the browser dialog for this challenge at once.
        assertFalse(piContext.isPasskeyPrepared());
    }
}
