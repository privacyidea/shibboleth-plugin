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
import org.privacyidea.Challenge;
import org.privacyidea.PIError;
import org.privacyidea.PIResponse;
import org.privacyidea.PrivacyIDEA;
import org.privacyidea.context.Config;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIFormContext;
import org.privacyidea.context.PIServerConfigContext;
import org.privacyidea.context.User;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Unit tests for the "Use" button of a code token (hotp/totp/sms/email) in the tokenSelection flow: the picked
 * serial is triggered, its transaction id and message drive the OTP form, and a token without a challenge falls
 * back to typed entry. Also covers the generic message for an unknown user at the OTP step.
 */
public class PrivacyIDEAAuthenticatorTokenSelectionTest
{
    private static final String SERIAL = "TOTP0001";

    private PrivacyIDEA privacyIDEA;
    private HttpServletRequest request;
    private ProfileRequestContext profileRequestContext;
    private PIContext piContext;
    private PIFormContext piFormContext;
    private PrivacyIDEAAuthenticator authenticator;

    @BeforeMethod
    public void setUp() throws Exception
    {
        privacyIDEA = mock(PrivacyIDEA.class);
        Config config = mock(Config.class);
        when(config.getAuthenticationFlow()).thenReturn("tokenSelection");

        request = mock(HttpServletRequest.class);

        profileRequestContext = new ProfileRequestContext();
        AuthenticationContext authenticationContext = profileRequestContext.ensureSubcontext(AuthenticationContext.class);
        piContext = new PIContext(new User("alice"), "test");
        piFormContext = new PIFormContext(null, null, null, false, null, false, false);
        authenticationContext.addSubcontext(piContext);
        authenticationContext.addSubcontext(piFormContext);
        authenticationContext.addSubcontext(new PIServerConfigContext(config));

        authenticator = new PrivacyIDEAAuthenticator();
        authenticator.setPrivacyIDEA(privacyIDEA);
        authenticator.setHttpServletRequestSupplier(() -> request);
        authenticator.initialize();
    }

    private void pickToken(String type)
    {
        when(request.getParameter("selectedType")).thenReturn(type);
        when(request.getParameter("selectedSerial")).thenReturn(SERIAL);
    }

    private String executeAndGetEvent()
    {
        authenticator.execute(profileRequestContext);
        EventContext eventContext = profileRequestContext.getSubcontext(EventContext.class);
        assertNotNull(eventContext);
        return String.valueOf(eventContext.getEvent());
    }

    private static PIResponse triggered(Challenge... challenges)
    {
        PIResponse response = new PIResponse();
        response.authentication = AuthenticationStatus.CHALLENGE;
        for (Challenge challenge : challenges)
        {
            response.multiChallenge.add(challenge);
            response.transactionID = challenge.getTransactionID();
        }
        return response;
    }

    @Test
    public void codeTokenUsesItsChallenge()
    {
        pickToken("totp");
        when(privacyIDEA.triggerChallenges(eq("alice"), eq(Map.of("serial", SERIAL)), anyMap()))
                .thenReturn(triggered(new Challenge(SERIAL, "please enter otp", "interactive", "", "tx1", "totp")));

        assertEquals(executeAndGetEvent(), "reload");
        assertEquals(piContext.getMode(), "otp");
        assertEquals(piContext.getTransactionID(), "tx1");
        assertEquals(piContext.getSelectedSerial(), SERIAL);
        assertEquals(piFormContext.getMessage(), "please enter otp");
    }

    @Test
    public void coTriggeredPasskeyDoesNotChangeTheMode()
    {
        pickToken("sms");
        PIResponse response = triggered(new Challenge(SERIAL, "Enter the OTP from the SMS:", "interactive", "", "tx2", "sms"));
        response.passkeyChallenge = "{\"challenge\":\"abc\"}";
        when(privacyIDEA.triggerChallenges(eq("alice"), eq(Map.of("serial", SERIAL)), anyMap())).thenReturn(response);

        assertEquals(executeAndGetEvent(), "reload");
        assertEquals(piContext.getMode(), "otp");
        assertEquals(piContext.getTransactionID(), "tx2");
        assertEquals(piContext.getPasskeyChallenge(), "");
    }

    @Test
    public void noChallengeFallsBackToTypedEntry()
    {
        pickToken("hotp");
        piContext.setTransactionID("old");
        piFormContext.setMessage("old message");
        PIResponse response = new PIResponse();
        response.authentication = AuthenticationStatus.REJECT;
        when(privacyIDEA.triggerChallenges(eq("alice"), eq(Map.of("serial", SERIAL)), anyMap())).thenReturn(response);

        assertEquals(executeAndGetEvent(), "reload");
        assertEquals(piContext.getMode(), "otp");
        assertEquals(piContext.getTransactionID(), "");
        // Back to the default prompt.
        assertEquals(piFormContext.getMessage(), "Please enter your One-Time-Password:");
    }

    @Test
    public void unknownUserAtOtpStepGetsGenericMessage()
    {
        when(request.getParameter("otp")).thenReturn("123456");
        PIResponse response = new PIResponse();
        response.error = new PIError(904, "ERR904: User <alice@realm> does not exist.");
        when(privacyIDEA.validateCheck(eq("alice"), eq("123456"), any(), anyMap(), anyMap())).thenReturn(response);

        assertEquals(executeAndGetEvent(), "reload");
        assertEquals(piContext.getFormErrorMessage(), "Authentication failed.");
    }

    @Test
    public void hiddenUserErrorAtOtpStepGetsGenericMessage()
    {
        // hide_specific_error_message in privacyIDEA 3.13 returns 4031 with an empty description for an unknown user.
        when(request.getParameter("otp")).thenReturn("123456");
        PIResponse response = new PIResponse();
        response.error = new PIError(4031, "ERR4031: ");
        when(privacyIDEA.validateCheck(eq("alice"), eq("123456"), any(), anyMap(), anyMap())).thenReturn(response);

        assertEquals(executeAndGetEvent(), "reload");
        assertEquals(piContext.getFormErrorMessage(), "Authentication failed.");
    }

    @Test
    public void otherServerErrorAtOtpStepIsShown()
    {
        when(request.getParameter("otp")).thenReturn("123456");
        PIResponse response = new PIResponse();
        response.error = new PIError(905, "ERR905: Missing parameter");
        when(privacyIDEA.validateCheck(eq("alice"), eq("123456"), any(), anyMap(), anyMap())).thenReturn(response);

        assertEquals(executeAndGetEvent(), "reload");
        assertEquals(piContext.getFormErrorMessage(), "ERR905: Missing parameter");
    }
}
