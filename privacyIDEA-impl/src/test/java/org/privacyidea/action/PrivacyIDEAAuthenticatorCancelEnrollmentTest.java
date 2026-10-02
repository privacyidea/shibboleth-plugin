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
import net.shibboleth.idp.authn.AuthenticationResult;
import net.shibboleth.idp.authn.context.AuthenticationContext;
import net.shibboleth.idp.authn.context.MultiFactorAuthenticationContext;
import net.shibboleth.idp.authn.principal.UsernamePrincipal;
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
import org.privacyidea.context.User;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.security.auth.Subject;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Unit tests for the "skip optional enrollment" submit ({@code cancelEnrollment=1}) of
 * {@link PrivacyIDEAAuthenticator}: the login is only finished when an optional enrollment was offered and
 * privacyIDEA confirms the cancellation; otherwise the form is shown again.
 */
public class PrivacyIDEAAuthenticatorCancelEnrollmentTest
{
    private static final String TRANSACTION_ID = "1234567890";

    private PrivacyIDEA privacyIDEA;
    private ProfileRequestContext profileRequestContext;
    private PIContext piContext;
    private PIFormContext piFormContext;
    private PrivacyIDEAAuthenticator authenticator;

    @BeforeMethod
    public void setUp() throws Exception
    {
        privacyIDEA = mock(PrivacyIDEA.class);
        Config config = mock(Config.class);
        when(config.getAuthenticationFlow()).thenReturn("default");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("cancelEnrollment")).thenReturn("1");

        profileRequestContext = new ProfileRequestContext();
        AuthenticationContext authenticationContext = profileRequestContext.ensureSubcontext(AuthenticationContext.class);
        piContext = new PIContext(new User("alice"), "test");
        piFormContext = new PIFormContext(null, null, null, false, null, false, false);
        authenticationContext.addSubcontext(piContext);
        authenticationContext.addSubcontext(piFormContext);
        authenticationContext.addSubcontext(new PIServerConfigContext(config));
        // enroll-via-multichallenge happens after a first factor succeeded, so model a second-factor run:
        // give the MFA context a result for "alice". privacyIDEA then completes with the "success" event.
        Subject subject = new Subject();
        subject.getPrincipals().add(new UsernamePrincipal("alice"));
        AuthenticationResult firstFactor = new AuthenticationResult("authn/test", subject);
        MultiFactorAuthenticationContext mfaContext = authenticationContext.ensureSubcontext(MultiFactorAuthenticationContext.class);
        mfaContext.getActiveResults().put(firstFactor.getAuthenticationFlowId(), firstFactor);

        authenticator = new PrivacyIDEAAuthenticator();
        authenticator.setPrivacyIDEA(privacyIDEA);
        authenticator.setHttpServletRequestSupplier(() -> request);
        authenticator.initialize();
    }

    private void offerOptionalEnrollment()
    {
        piContext.setTransactionID(TRANSACTION_ID);
        piFormContext.setEnrollViaMultichallenge(true);
        piFormContext.setEnrollViaMultichallengeOptional(true);
    }

    private String executeAndGetEvent()
    {
        authenticator.execute(profileRequestContext);
        EventContext eventContext = profileRequestContext.getSubcontext(EventContext.class);
        assertNotNull(eventContext);
        return String.valueOf(eventContext.getEvent());
    }

    private static PIResponse response(boolean value, AuthenticationStatus status)
    {
        PIResponse response = new PIResponse();
        response.value = value;
        response.authentication = status;
        return response;
    }

    @Test
    public void confirmedCancellationFinishesLogin()
    {
        offerOptionalEnrollment();
        when(privacyIDEA.validateCheckCancelEnrollment(eq(TRANSACTION_ID), anyMap()))
                .thenReturn(response(true, AuthenticationStatus.ACCEPT));

        assertEquals(executeAndGetEvent(), "success");
    }

    @Test
    public void unconfirmedCancellationReloadsForm()
    {
        offerOptionalEnrollment();
        when(privacyIDEA.validateCheckCancelEnrollment(eq(TRANSACTION_ID), anyMap()))
                .thenReturn(response(false, AuthenticationStatus.REJECT));

        assertEquals(executeAndGetEvent(), "reload");
    }

    @Test
    public void serverErrorReloadsForm()
    {
        offerOptionalEnrollment();
        PIResponse error = new PIResponse();
        error.error = new PIError(905, "ERR905: Missing parameter");
        when(privacyIDEA.validateCheckCancelEnrollment(eq(TRANSACTION_ID), anyMap())).thenReturn(error);

        assertEquals(executeAndGetEvent(), "reload");
    }

    @Test
    public void noResponseReloadsForm()
    {
        offerOptionalEnrollment();
        when(privacyIDEA.validateCheckCancelEnrollment(eq(TRANSACTION_ID), anyMap())).thenReturn(null);

        assertEquals(executeAndGetEvent(), "reload");
    }

    @Test
    public void noPendingEnrollmentReloadsFormWithoutContactingServer()
    {
        piContext.setTransactionID(TRANSACTION_ID);

        assertEquals(executeAndGetEvent(), "reload");
        verify(privacyIDEA, never()).validateCheckCancelEnrollment(anyString(), any());
    }

    @Test
    public void mandatoryEnrollmentCannotBeCancelled()
    {
        offerOptionalEnrollment();
        piFormContext.setEnrollViaMultichallengeOptional(false);

        assertEquals(executeAndGetEvent(), "reload");
        verify(privacyIDEA, never()).validateCheckCancelEnrollment(anyString(), any());
    }
}
