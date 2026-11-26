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

import net.shibboleth.idp.authn.AuthenticationResult;
import net.shibboleth.idp.authn.context.AuthenticationContext;
import net.shibboleth.idp.authn.context.MultiFactorAuthenticationContext;
import net.shibboleth.idp.authn.principal.UsernamePrincipal;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.context.PIFormContext;
import org.testng.annotations.Test;

import javax.security.auth.Subject;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * {@code password_by_idp}: the first page offers the IdP's password flow only on the first run of the plugin in an
 * MFA process. After the password flow (a fresh result), the plugin runs as the second factor without that offer.
 */
public class InitializePIContextPasswordStepTest
{
    private static PIFormContext run(boolean passwordByIdp, boolean inMfa, boolean afterPassword) throws Exception
    {
        ProfileRequestContext prc = new ProfileRequestContext();
        AuthenticationContext ac = prc.ensureSubcontext(AuthenticationContext.class);
        if (inMfa)
        {
            MultiFactorAuthenticationContext mfa = ac.ensureSubcontext(MultiFactorAuthenticationContext.class);
            if (afterPassword)
            {
                Subject subject = new Subject();
                subject.getPrincipals().add(new UsernamePrincipal("alice"));
                AuthenticationResult password = new AuthenticationResult("authn/Password", subject);
                mfa.getActiveResults().put(password.getAuthenticationFlowId(), password);
            }
        }

        InitializePIContext action = new InitializePIContext();
        action.setAuthenticationFlow("tokenSelection");
        action.setPasswordByIdp(passwordByIdp);
        action.initialize();
        action.execute(prc);

        PIFormContext piFormContext = ac.getSubcontext(PIFormContext.class);
        assertNotNull(piFormContext);
        return piFormContext;
    }

    @Test
    public void firstRunInMfaOffersPasswordStep() throws Exception
    {
        assertTrue(run(true, true, false).isPasswordStepOffered());
    }

    @Test
    public void runAfterPasswordFlowDoesNotOfferPasswordStep() throws Exception
    {
        assertFalse(run(true, true, true).isPasswordStepOffered());
    }

    @Test
    public void withoutMfaThePasswordStepIsNotOffered() throws Exception
    {
        assertFalse(run(true, false, false).isPasswordStepOffered());
    }

    @Test
    public void settingOffDoesNotOfferPasswordStep() throws Exception
    {
        assertFalse(run(false, true, false).isPasswordStepOffered());
    }
}
