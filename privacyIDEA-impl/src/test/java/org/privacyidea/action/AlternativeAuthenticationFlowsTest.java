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
import org.privacyidea.TokenInfo;
import org.privacyidea.context.Config;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIFormContext;
import org.privacyidea.context.PIServerConfigContext;
import org.privacyidea.context.TokenListEntry;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Unit tests for the answers of {@link AlternativeAuthenticationFlows} to a rejected password and to an unknown
 * user (privacyIDEA ERR904, or 403/4031 with the auth policy hide_specific_error_message): the default flow shows
 * the username form again with one message for both, and the flows without a password field continue to the OTP
 * form instead of ending the login with an error. Also the token types that the tokenSelection list leaves out.
 */
public class AlternativeAuthenticationFlowsTest
{
    private static final String WRONG_CREDENTIALS = "Wrong username or password.";

    private PrivacyIDEA privacyIDEA;
    private Config config;
    private ProfileRequestContext profileRequestContext;
    private PIContext piContext;
    private PIFormContext piFormContext;
    private AlternativeAuthenticationFlows action;

    /** Standalone run (no preceding factor): the username and password come from the plugin's own form. */
    private void setUp(String flow, String password) throws Exception
    {
        privacyIDEA = mock(PrivacyIDEA.class);
        config = mock(Config.class);
        when(config.getAuthenticationFlow()).thenReturn(flow);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("username")).thenReturn("alice");
        when(request.getParameter("otp")).thenReturn(password);
        when(request.getLocale()).thenReturn(Locale.US);

        profileRequestContext = new ProfileRequestContext();
        AuthenticationContext authenticationContext = profileRequestContext.ensureSubcontext(AuthenticationContext.class);
        piContext = new PIContext(null, "test");
        authenticationContext.addSubcontext(piContext);
        piFormContext = new PIFormContext(null, null, null, false, null, false, false);
        authenticationContext.addSubcontext(piFormContext);
        authenticationContext.addSubcontext(new PIServerConfigContext(config));

        action = new AlternativeAuthenticationFlows();
        action.setPrivacyIDEA(privacyIDEA);
        action.setHttpServletRequestSupplier(() -> request);
        action.initialize();
    }

    /** @return the event the action built, or {@code null} if it built none (the flow then proceeds to the OTP form) */
    private String executeAndGetEvent()
    {
        action.execute(profileRequestContext);
        EventContext eventContext = profileRequestContext.getSubcontext(EventContext.class);
        return eventContext == null ? null : String.valueOf(eventContext.getEvent());
    }

    private static PIResponse reject()
    {
        PIResponse response = new PIResponse();
        response.value = false;
        response.authentication = AuthenticationStatus.REJECT;
        response.message = "wrong otp pin";
        return response;
    }

    private static PIResponse error(int code)
    {
        PIResponse response = new PIResponse();
        response.error = new PIError(code, "ERR" + code + ": something");
        return response;
    }

    @Test
    public void defaultFlowWrongPasswordRedisplaysUsernameForm() throws Exception
    {
        setUp("default", "wrong");
        when(privacyIDEA.validateCheck(eq("alice"), eq("wrong"), anyMap())).thenReturn(reject());

        assertEquals(executeAndGetEvent(), "redisplayUsernameForm");
        assertEquals(piContext.getFormErrorMessage(), WRONG_CREDENTIALS);
    }

    @Test
    public void defaultFlowUnknownUserGetsSameMessageAsWrongPassword() throws Exception
    {
        setUp("default", "secret");
        when(privacyIDEA.validateCheck(eq("alice"), eq("secret"), anyMap())).thenReturn(error(904));

        assertEquals(executeAndGetEvent(), "redisplayUsernameForm");
        assertEquals(piContext.getFormErrorMessage(), WRONG_CREDENTIALS);
    }

    @Test
    public void defaultFlowHiddenUserErrorGetsSameMessageAsWrongPassword() throws Exception
    {
        // hide_specific_error_message: 4031 in privacyIDEA 3.13, 403 in 3.14 and later
        for (int code : new int[]{403, 4031})
        {
            setUp("default", "secret");
            when(privacyIDEA.validateCheck(eq("alice"), eq("secret"), anyMap())).thenReturn(error(code));

            assertEquals(executeAndGetEvent(), "redisplayUsernameForm", String.valueOf(code));
            assertEquals(piContext.getFormErrorMessage(), WRONG_CREDENTIALS, String.valueOf(code));
        }
    }

    @Test
    public void sendStaticPassHiddenUserErrorContinuesToOtpForm() throws Exception
    {
        setUp("sendStaticPass", null);
        when(config.getStaticPass()).thenReturn("static");
        when(privacyIDEA.validateCheck(eq("alice"), eq("static"), anyMap())).thenReturn(error(403));

        assertNull(executeAndGetEvent());
    }

    @Test
    public void defaultFlowServerErrorStillEndsWithAuthenticationException() throws Exception
    {
        setUp("default", "secret");
        when(privacyIDEA.validateCheck(eq("alice"), eq("secret"), anyMap())).thenReturn(error(905));

        assertEquals(executeAndGetEvent(), "AuthenticationException");
    }

    @Test
    public void defaultFlowChallengeContinuesToOtpForm() throws Exception
    {
        setUp("default", "secret");
        PIResponse challenge = reject();
        challenge.transactionID = "tx1";
        challenge.multiChallenge.add(new Challenge("TOTP0001", "please enter otp", "interactive", "", "tx1", "totp"));
        when(privacyIDEA.validateCheck(eq("alice"), eq("secret"), anyMap())).thenReturn(challenge);

        assertNull(executeAndGetEvent());
        assertEquals(piContext.getTransactionID(), "tx1");
        assertNull(piContext.getFormErrorMessage());
    }

    @Test
    public void defaultFlowBlankPasswordContinuesToOtpFormWithoutRequest() throws Exception
    {
        setUp("default", "");

        assertNull(executeAndGetEvent());
    }

    @Test
    public void triggerChallengeUnknownUserContinuesToOtpForm() throws Exception
    {
        setUp("triggerChallenge", null);
        when(privacyIDEA.triggerChallenges(eq("alice"), anyMap(), anyMap())).thenReturn(error(904));

        assertNull(executeAndGetEvent());
    }

    @Test
    public void triggerChallengeServerErrorStillEndsWithAuthenticationException() throws Exception
    {
        // hide_specific_error_message does not change /validate/triggerchallenge, so 403 is a real error there.
        for (int code : new int[]{905, 403})
        {
            setUp("triggerChallenge", null);
            when(privacyIDEA.triggerChallenges(eq("alice"), anyMap(), anyMap())).thenReturn(error(code));

            assertEquals(executeAndGetEvent(), "AuthenticationException", String.valueOf(code));
        }
    }

    private static TokenInfo token(String serial, String type)
    {
        TokenInfo token = new TokenInfo();
        token.serial = serial;
        token.tokenType = type;
        token.active = true;
        token.rolloutState = "enrolled";
        return token;
    }

    @Test
    public void tokenSelectionLeavesOutTokensThatCannotLogIn() throws Exception
    {
        setUp("tokenSelection", null);
        when(privacyIDEA.getTokenInfo(eq("alice"), anyMap())).thenReturn(List.of(
                token("HOTP1", "hotp"), token("CERT1", "certificate"), token("SSH1", "sshkey"),
                token("APP1", "applspec"), token("DEPR1", "deprecated"), token("PW1", "spass")));

        assertNull(executeAndGetEvent());
        assertEquals(piFormContext.getTokens().stream().map(TokenListEntry::getSerial).toList(), List.of("HOTP1", "PW1"));
    }

    @Test
    public void preparedPasskeyChallengeEndsWhenUsernameIsSubmitted() throws Exception
    {
        // passkey_ui conditional: the user typed the username instead of using the passkey autofill.
        setUp("default", "secret");
        piContext.setPasskeyChallenge("{\"challenge\":\"abc\"}");
        piContext.setPasskeyTransactionID("tx1");
        piContext.setPasskeyPrepared(true);
        when(privacyIDEA.validateCheck(eq("alice"), eq("secret"), anyMap())).thenReturn(reject());

        executeAndGetEvent();

        assertEquals(piContext.getPasskeyChallenge(), "");
        assertNull(piContext.getPasskeyTransactionID());
    }
}
