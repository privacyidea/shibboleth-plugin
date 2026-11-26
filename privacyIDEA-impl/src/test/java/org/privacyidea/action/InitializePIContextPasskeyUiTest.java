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
import org.privacyidea.context.PIFormContext;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * passkey_ui and passkey_button: the configured value reaches the username form. An empty or unknown value gives the
 * button mode, which is the behavior before the setting.
 */
public class InitializePIContextPasskeyUiTest
{
    private static PIFormContext run(String passkeyUi, boolean passkeyButton, String usernameFieldName) throws Exception
    {
        ProfileRequestContext prc = new ProfileRequestContext();
        AuthenticationContext ac = prc.ensureSubcontext(AuthenticationContext.class);

        InitializePIContext action = new InitializePIContext();
        action.setAuthenticationFlow("tokenSelection");
        action.setPasskeyUi(passkeyUi);
        action.setPasskeyButton(passkeyButton);
        action.setPasswordUsernameFieldName(usernameFieldName);
        action.initialize();
        action.execute(prc);

        PIFormContext piFormContext = ac.getSubcontext(PIFormContext.class);
        assertNotNull(piFormContext);
        return piFormContext;
    }

    @Test
    public void defaultIsButton() throws Exception
    {
        PIFormContext form = run(null, true, null);
        assertEquals(form.getPasskeyUi(), "button");
        assertFalse(form.preparesPasskeyChallenge());
        assertTrue(form.isPasskeyButtonShown());
        assertEquals(form.getPasswordUsernameFieldName(), "j_username");
    }

    @Test
    public void valueIsCaseInsensitive() throws Exception
    {
        PIFormContext form = run(" Conditional ", true, "user");
        assertEquals(form.getPasskeyUi(), "conditional");
        assertTrue(form.preparesPasskeyChallenge());
        assertEquals(form.getPasswordUsernameFieldName(), "user");
    }

    @Test
    public void unknownValueGivesButton() throws Exception
    {
        assertEquals(run("autofill", true, null).getPasskeyUi(), "button");
    }

    @Test
    public void passkeyButtonFalseHidesButtonOnlyForConditionalAndImmediate() throws Exception
    {
        assertFalse(run("immediate", false, null).isPasskeyButtonShown());
        assertFalse(run("conditional", false, null).isPasskeyButtonShown());
        assertTrue(run("button", false, null).isPasskeyButtonShown());
    }
}
