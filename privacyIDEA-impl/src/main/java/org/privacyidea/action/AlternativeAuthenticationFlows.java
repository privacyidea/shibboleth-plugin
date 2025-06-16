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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import org.opensaml.profile.action.ActionSupport;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.PIResponse;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIServerConfigContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlternativeAuthenticationFlows extends ChallengeResponseAction
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AlternativeAuthenticationFlows.class);

    public AlternativeAuthenticationFlows() {}

    @Override
    protected final void doExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext, @Nonnull PIServerConfigContext piServerConfigContext)
    {
        if (piServerConfigContext.getConfigParams().getAuthenticationFlow().equals("triggerChallenge"))
        {
            if (debug)
            {
                LOGGER.info("{} Authentication flow - triggerChallenge.", this.getLogPrefix());
            }

            HttpServletRequest request = Objects.requireNonNull(getHttpServletRequestSupplier()).get();
            Map<String, String> headers = this.getHeadersToForward(request);
            PIResponse piResponse = privacyIDEA.triggerChallenges(piContext.getUsername(), Collections.emptyMap(), headers);

            if (piResponse != null)
            {
                if (piResponse.error != null)
                {
                    LOGGER.error("{} privacyIDEA server error: {}!", this.getLogPrefix(), piResponse.error.message);
                    ActionSupport.buildEvent(profileRequestContext, "AuthenticationException");
                    return;
                }

                if (!piResponse.multiChallenge.isEmpty())
                {
                    if (debug)
                    {
                        LOGGER.debug("{} Extracting the form data from triggered challenges...", this.getLogPrefix());
                    }
                    extractChallengeData(piResponse);
                    extractMessage(piResponse);
                }
            }
            else
            {
                LOGGER.error("{} triggerChallenge failed. Response was null. Fallback to standard procedure.", this.getLogPrefix());
            }
        }
        else if (piServerConfigContext.getConfigParams().getAuthenticationFlow().equals("sendStaticPass"))
        {
            if (debug)
            {
                LOGGER.info("{} Authentication flow - sendStaticPass.", this.getLogPrefix());
            }

            if (piServerConfigContext.getConfigParams().getStaticPass() == null)
            {
                LOGGER.error("{} Static pass isn't set. Fallback to default authentication flow...", this.getLogPrefix());
            }
            else
            {
                // Call /validate/check with a static pass from the configuration
                // This could already end the authentication if the "passOnNoToken" policy is set.
                // Otherwise, it might trigger the challenges.
                HttpServletRequest request = Objects.requireNonNull(this.getHttpServletRequestSupplier()).get();
                Map<String, String> headers = this.getHeadersToForward(request);
                PIResponse piResponse = privacyIDEA.validateCheck(piContext.getUsername(), piServerConfigContext.getConfigParams().getStaticPass(), headers);

                if (piResponse != null)
                {
                    if (piResponse.error != null)
                    {
                        LOGGER.error("{} privacyIDEA server error: {}!", this.getLogPrefix(), piResponse.error.message);
                        ActionSupport.buildEvent(profileRequestContext, "AuthenticationException");
                        return;
                    }
                    extractMessage(piResponse);
                    
                    if (piResponse.value)
                    {
                        if (debug)
                        {
                            LOGGER.info("{} Authentication succeeded!", this.getLogPrefix());
                        }
                        ActionSupport.buildEvent(profileRequestContext, "success");
                    }

                    if (!piResponse.multiChallenge.isEmpty())
                    {
                        extractChallengeData(piResponse);
                    }
                }
            }
        }
        else
        {
            if (debug)
            {
                LOGGER.info("{} Authentication flow: default.", this.getLogPrefix());
            }
        }
    }
}