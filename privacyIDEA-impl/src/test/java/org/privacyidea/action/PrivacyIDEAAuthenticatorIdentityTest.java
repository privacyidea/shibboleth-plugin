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
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.PrivacyIDEA;
import org.privacyidea.context.Config;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIFormContext;
import org.privacyidea.context.PIServerConfigContext;
import org.privacyidea.context.User;
import org.testng.annotations.Test;

import javax.security.auth.Subject;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * The submitted {@code username}, {@code standalone} and {@code origin} fields must not decide which user
 * privacyIDEA validates, how the result is asserted, or the WebAuthn origin. With a preceding factor the
 * user and the (cleared) standalone flag come from that factor's result; the origin comes from the request.
 */
public class PrivacyIDEAAuthenticatorIdentityTest
{
    private PIContext run(boolean withFirstFactor, HttpServletRequest request) throws Exception
    {
        Config config = mock(Config.class);
        when(config.getAuthenticationFlow()).thenReturn("default");

        ProfileRequestContext prc = new ProfileRequestContext();
        AuthenticationContext ac = prc.ensureSubcontext(AuthenticationContext.class);
        PIContext piContext = new PIContext(new User("alice"), "test");
        ac.addSubcontext(piContext);
        ac.addSubcontext(new PIFormContext(null, null, null, false, null, false, false));
        ac.addSubcontext(new PIServerConfigContext(config));

        MultiFactorAuthenticationContext mfa = ac.ensureSubcontext(MultiFactorAuthenticationContext.class);
        if (withFirstFactor)
        {
            Subject subject = new Subject();
            subject.getPrincipals().add(new UsernamePrincipal("alice"));
            AuthenticationResult first = new AuthenticationResult("authn/test", subject);
            mfa.getActiveResults().put(first.getAuthenticationFlowId(), first);
        }

        PrivacyIDEAAuthenticator authenticator = new PrivacyIDEAAuthenticator();
        authenticator.setPrivacyIDEA(mock(PrivacyIDEA.class));
        authenticator.setHttpServletRequestSupplier(() -> request);
        authenticator.initialize();
        authenticator.execute(prc);
        return piContext;
    }

    private static HttpServletRequest baseRequest()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("idp.example.org");
        when(request.getServerPort()).thenReturn(8443);
        return request;
    }

    @Test
    public void secondFactorKeepsFirstFactorUserAndIsNotStandalone() throws Exception
    {
        HttpServletRequest request = baseRequest();
        when(request.getParameter("username")).thenReturn("bob");
        when(request.getParameter("standalone")).thenReturn("1");

        PIContext piContext = run(true, request);

        assertEquals(piContext.getUsername(), "alice", "submitted username must be ignored for a second factor");
        assertFalse("1".equals(piContext.getStandalone()), "submitted standalone=1 must be ignored for a second factor");
    }

    @Test
    public void firstOrOnlyFactorIsStandalone() throws Exception
    {
        PIContext piContext = run(false, baseRequest());
        assertTrue("1".equals(piContext.getStandalone()), "without a preceding factor the run is standalone");
    }

    @Test
    public void originComesFromHeaderNotForm() throws Exception
    {
        HttpServletRequest request = baseRequest();
        when(request.getHeader("Origin")).thenReturn("https://idp.example.org");
        when(request.getParameter("origin")).thenReturn("https://attacker.example");

        PIContext piContext = run(true, request);

        assertEquals(piContext.getOrigin(), "https://idp.example.org");
    }

    @Test
    public void originFallsBackToRequestWhenHeaderAbsent() throws Exception
    {
        HttpServletRequest request = baseRequest();
        when(request.getParameter("origin")).thenReturn("https://attacker.example");

        PIContext piContext = run(true, request);

        assertEquals(piContext.getOrigin(), "https://idp.example.org:8443");
    }
}
