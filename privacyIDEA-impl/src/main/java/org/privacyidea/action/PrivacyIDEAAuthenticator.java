/*
 * Copyright 2024 NetKnights GmbH - lukas.matusiewicz@netknights.it
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

import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import org.opensaml.profile.action.ActionSupport;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.PIResponse;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIServerConfigContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrivacyIDEAAuthenticator extends ChallengeResponseAction
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PrivacyIDEAAuthenticator.class);

    public PrivacyIDEAAuthenticator() {}

    @Override
    protected final void doExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext, @Nonnull PIServerConfigContext piServerConfigContext)
    {
        HttpServletRequest request = Objects.requireNonNull(this.getHttpServletRequestSupplier()).get();
        Map<String, String> headers = this.getHeadersToForward(request);

        piContext.setMode(request.getParameterValues("mode")[0]);
        piContext.setWebauthnSignResponse(request.getParameterValues("webauthnSignResponse")[0]);
        piContext.setOrigin(request.getParameterValues("origin")[0]);
        piContext.setFormErrorMessage(request.getParameterValues("errorMessage")[0]);

        PIResponse piResponse = null;
        if (request.getParameterValues("silentModeChange")[0].equals("1"))
        {
            ActionSupport.buildEvent(profileRequestContext, "reload");
            return;
        }
        else if (piContext.getMode().equals("push"))
        {
            // In push mode, poll for the transaction id to see if the challenge has been answered
            if (privacyIDEA.pollTransaction(piContext.getTransactionID()))
            {
                // If the challenge has been answered, finalize with a call to validate check
                piResponse = privacyIDEA.validateCheck(piContext.getUsername(), "", piContext.getTransactionID(), headers);
            }
            else
            {
                if (debug)
                {
                    LOGGER.info("{} Push token isn't confirmed yet...", this.getLogPrefix());
                }
                ActionSupport.buildEvent(profileRequestContext, "reload");
                return;
            }
        }
        else if (piContext.getWebauthnSignResponse() != null && !piContext.getWebauthnSignResponse().isEmpty())
        {
            if (piContext.getOrigin() == null || piContext.getOrigin().isEmpty())
            {
                LOGGER.error("Origin is missing for WebAuthn authentication!");
            }
            else
            {
                piResponse = privacyIDEA.validateCheckWebAuthn(piContext.getUsername(), piContext.getTransactionID(), piContext.getWebauthnSignResponse(),
                                                               piContext.getOrigin(), headers);
            }
        }
        else if (piContext.getMode().equals("otp"))
        {
            if (request.getParameterValues("otp") != null)
            {
                String otp = request.getParameterValues("otp")[0];
                if (otp != null)
                {
                    piResponse = privacyIDEA.validateCheck(piContext.getUsername(), otp, piContext.getTransactionID(), headers);
                }
                else
                {
                    if (debug)
                    {
                        LOGGER.info("{} Cannot send password because it is null!", this.getLogPrefix());
                    }
                    return;
                }
            }
        }
        else
        {
            ActionSupport.buildEvent(profileRequestContext, "reload");
            return;
        }

        if (piResponse != null)
        {
            if (debug)
            {
                LOGGER.info("{} Extracting data from the response...", this.getLogPrefix());
            }
            extractMessage(piResponse);

            if (piResponse.error != null)
            {
                LOGGER.error("{} privacyIDEA server error: {}!", this.getLogPrefix(), piResponse.error.message);
                piContext.setFormErrorMessage(piResponse.error.message);
                ActionSupport.buildEvent(profileRequestContext, "reload");
            }
            else if (!piResponse.multichallenge.isEmpty())
            {
                if (debug)
                {
                    LOGGER.info("{} Challenge triggered, building form...", this.getLogPrefix());
                }
                extractChallengeData(piResponse);
                ActionSupport.buildEvent(profileRequestContext, "reload");
            }
            else if (piResponse.value)
            {
                if (debug)
                {
                    LOGGER.info("{} Authentication succeeded!", this.getLogPrefix());
                }
                ActionSupport.buildEvent(profileRequestContext, "success");
            }
            else
            {
                if (debug)
                {
                    LOGGER.info("{} Received response from server, building form...", this.getLogPrefix());
                }
                ActionSupport.buildEvent(profileRequestContext, "reload");
            }
        }
        else
        {
            LOGGER.error("{} privacyIDEA response was null. Please check the config and try again.", this.getLogPrefix());
            ActionSupport.buildEvent(profileRequestContext, "reload");
        }
    }
}