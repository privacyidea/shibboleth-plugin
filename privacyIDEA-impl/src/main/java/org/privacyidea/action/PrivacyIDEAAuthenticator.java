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

import net.shibboleth.idp.authn.context.UsernameContext;
import org.opensaml.profile.action.ActionSupport;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.ChallengeStatus;
import org.privacyidea.PIResponse;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIServerConfigContext;
import org.privacyidea.context.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class PrivacyIDEAAuthenticator extends ChallengeResponseAction
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PrivacyIDEAAuthenticator.class);

    public PrivacyIDEAAuthenticator() {}

    @Override
    protected final void doExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext,
                                   @Nonnull PIServerConfigContext piServerConfigContext)
    {
        HttpServletRequest request = Objects.requireNonNull(getHttpServletRequestSupplier()).get();
        Map<String, String> headers = this.getHeadersToForward(request);

        piContext.setMode(request.getParameterValues("mode")[0]);
        piContext.setWebauthnSignResponse(request.getParameterValues("webauthnSignResponse")[0]);
        piContext.setPasskeySignResponse(request.getParameterValues("passkeySignResponse")[0]);
        piContext.setPasskeyRegistration(request.getParameterValues("passkeyRegistration")[0]);
        piContext.setPasskeyRegistrationResponse(request.getParameterValues("passkeyRegistrationResponse")[0]);
        piContext.setPasskeyChallenge(request.getParameterValues("passkeyChallenge")[0]);
        piContext.setOrigin(request.getParameterValues("origin")[0]);
        piContext.setFormErrorMessage(request.getParameterValues("errorMessage")[0]);
        if (request.getParameterValues("standalone") != null && StringUtil.isNotBlank(request.getParameterValues("standalone")[0]))
        {
            piContext.setStandalone(request.getParameterValues("standalone")[0]);
        }
        if (request.getParameterValues("username") != null && StringUtil.isNotBlank(request.getParameterValues("username")[0]))
        {
            piContext.setUsername(request.getParameterValues("username")[0]);
        }

        PIResponse piResponse = null;

        // Passkey: Sets the username collected from the privacyIDEA server and ends the authentication on success.
        if (StringUtil.isNotBlank(piContext.getPasskeySignResponse()))
        {
            if (StringUtil.isBlank(piContext.getOrigin()))
            {
                LOGGER.error("Origin is missing for Passkey authentication!");
            }
            else
            {
                String passkeyTransactionID = piContext.getPasskeyTransactionID();
                piResponse = privacyIDEA.validateCheckPasskey(passkeyTransactionID,
                                                              piContext.getPasskeySignResponse(),
                                                              piContext.getOrigin(),
                                                              headers);
                if (piResponse != null)
                {
                    if (piResponse.value)
                    {
                        if (StringUtil.isNotBlank(piResponse.username))
                        {
                            UsernameContext userCtx = profileRequestContext.getSubcontext(UsernameContext.class, true);
                            userCtx.setUsername(piResponse.username);
                            ActionSupport.buildEvent(profileRequestContext, "validateResponseStandalone");
                            return;
                        }
                    }
                }
            }
        }
        // Passkey login requested: Get a challenge and return
        if (request.getParameterValues("passkeyLoginRequested")[0].equals("1"))
        {
            PIResponse response = privacyIDEA.validateInitialize("passkey");
            if (StringUtil.isNotBlank(response.passkeyChallenge))
            {
                piContext.setPasskeyChallenge(response.passkeyChallenge);
                piContext.setMode("passkey");
                piContext.setPasskeyTransactionID(response.transactionID);

                ActionSupport.buildEvent(profileRequestContext, "reload");
                return;
            }
        }
        // Passkey login cancelled: Remove the challenge and passkey transaction ID
        if (request.getParameterValues("passkeyLoginCancelled")[0].equals("1"))
        {
            piContext.setPasskeyChallenge("");
            piContext.setPasskeyTransactionID(null);
        }
        // Passkey registration: enroll_via_multichallenge, this happens after successful authentication
        if (StringUtil.isNotBlank(piContext.getPasskeyRegistrationResponse()))
        {
            PIResponse response = privacyIDEA.validateCheckCompletePasskeyRegistration(piContext.getTransactionID(),
                                                                                       piContext.getPasskeyRegistrationSerial(),
                                                                                       piContext.getUsername(),
                                                                                       piContext.getPasskeyRegistrationResponse(),
                                                                                       piContext.getOrigin(),
                                                                                       headers);
            if (response != null)
            {
                if (response.error != null)
                {
                    LOGGER.error(response.error.message);
                    ActionSupport.buildEvent(profileRequestContext, "abort");
                    return;
                }
                else if (response.value)
                {
                    finalizeAuthentication(profileRequestContext, piContext);
                }
            }
        }

        if (request.getParameterValues("silentModeChange")[0].equals("1"))
        {
            ActionSupport.buildEvent(profileRequestContext, "reload");
            return;
        }
        else if (piContext.getMode().equals("push"))
        {
            // In push mode, poll for the transaction id to see if the challenge has been answered
            ChallengeStatus pollTransStatus = privacyIDEA.pollTransaction(piContext.getTransactionID());
            if (pollTransStatus == ChallengeStatus.accept)
            {
                // If the challenge has been answered, finalize with a call to validate check
                piResponse = privacyIDEA.validateCheck(piContext.getUsername(), "", piContext.getTransactionID(), headers);
            }
            else if (pollTransStatus == ChallengeStatus.pending)
            {
                if (debug)
                {
                    LOGGER.info("{} Push token isn't confirmed yet...", this.getLogPrefix());
                }
                ActionSupport.buildEvent(profileRequestContext, "reload");
                return;
            }
            else if (pollTransStatus == ChallengeStatus.declined)
            {
                if (debug)
                {
                    LOGGER.info("{} Push token was declined...", this.getLogPrefix());
                }
                piContext.setFormErrorMessage("Authentication declined!");
                ActionSupport.buildEvent(profileRequestContext, "abort");
                return;
            }
            else
            {
                if (debug)
                {
                    LOGGER.info("{} Push token failed...", this.getLogPrefix());
                }
                piContext.setFormErrorMessage("Push token failed!");
                ActionSupport.buildEvent(profileRequestContext, "reload");
                return;
            }
        }
        else if (StringUtil.isNotBlank(piContext.getWebauthnSignResponse()))
        {
            if (StringUtil.isBlank(piContext.getOrigin()))
            {
                LOGGER.error("Origin is missing for WebAuthn authentication!");
            }
            else
            {
                piResponse = privacyIDEA.validateCheckWebAuthn(piContext.getUsername(),
                                                               piContext.getTransactionID(),
                                                               piContext.getWebauthnSignResponse(),
                                                               piContext.getOrigin(),
                                                               new LinkedHashMap<>(),
                                                               headers);
            }
        }
        else if (piContext.getMode().equals("otp"))
        {
            if (request.getParameterValues("otp") != null && request.getParameterValues("otp").length > 0)
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
            else if (!piResponse.multiChallenge.isEmpty())
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
                finalizeAuthentication(profileRequestContext, piContext);
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

    /**
     * Finalizes the authentication process by building the success event.
     * In standalone mode plugin additionally sets the username in UsernameContext and then build the appropriate event.
     *
     * @param profileRequestContext The ProfileRequestContext for the current request.
     * @param piContext             The PIContext containing the authentication details.
     */
    private void finalizeAuthentication(@Nonnull ProfileRequestContext profileRequestContext,
                                        @Nonnull PIContext piContext)
    {
        if (StringUtil.isNotBlank(piContext.getStandalone()) && piContext.getStandalone().equals("1"))
        {
            if (debug)
            {
                LOGGER.info("{} Standalone mode, setting username and building event...", this.getLogPrefix());
            }
            UsernameContext userCtx = profileRequestContext.getSubcontext(UsernameContext.class, true);
            assert userCtx != null;
            userCtx.setUsername(piContext.getUsername());
            ActionSupport.buildEvent(profileRequestContext, "validateResponseStandalone");
        }
        else
        {
            if (debug)
            {
                LOGGER.info("{} Authentication successful, building success event...", this.getLogPrefix());
            }
            ActionSupport.buildEvent(profileRequestContext, "success");
        }
    }
}