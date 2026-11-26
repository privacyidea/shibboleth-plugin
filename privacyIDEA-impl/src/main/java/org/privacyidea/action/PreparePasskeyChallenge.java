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

import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.PIResponse;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIServerConfigContext;
import org.privacyidea.context.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * passkey_ui conditional or immediate: creates a usernameless passkey challenge each time the username form renders.
 * The browser needs the challenge before the user acts: the autofill request starts when the page loads, and the
 * immediate request must start in the click handler of the submit button.
 * <p>
 * The flow runs this action in the {@code on-render} of the username form. It does not send an event.
 */
public class PreparePasskeyChallenge extends ChallengeResponseAction
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PreparePasskeyChallenge.class);

    @Override
    protected void doExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext,
                             @Nonnull PIServerConfigContext piServerConfigContext)
    {
        if (!piFormContext.preparesPasskeyChallenge())
        {
            return;
        }
        // The passkey button reached the server without the page script, and the server created a challenge for it.
        // The page opens the browser dialog for that challenge, so keep it.
        if (!piContext.isPasskeyPrepared() && StringUtil.isNotBlank(piContext.getPasskeyChallenge()))
        {
            return;
        }
        // Each page view gets a new challenge, because privacyIDEA accepts a challenge only for its validity time.
        piContext.clearPasskeyChallenge();
        PIResponse response = privacyIDEA.validateInitialize("passkey");
        if (response != null && StringUtil.isNotBlank(response.passkeyChallenge))
        {
            piContext.setPasskeyChallenge(response.passkeyChallenge);
            piContext.setPasskeyTransactionID(response.transactionID);
            piContext.setPasskeyPrepared(true);
        }
        else
        {
            LOGGER.warn("{} Could not create a passkey challenge for passkey_ui={}. The username form shows the passkey button instead.",
                        getLogPrefix(), piFormContext.getPasskeyUi());
        }
    }
}
