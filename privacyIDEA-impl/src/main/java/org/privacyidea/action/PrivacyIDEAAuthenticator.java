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
import org.privacyidea.AuthenticationStatus;
import org.privacyidea.ChallengeStatus;
import org.privacyidea.PIResponse;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIServerConfigContext;
import org.privacyidea.context.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
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

        String mode = request.getParameter("mode");
        if (mode != null)
        {
            piContext.setMode(mode);
        }
        piContext.setWebauthnSignResponse(request.getParameter("webauthnSignResponse"));
        piContext.setPasskeySignResponse(request.getParameter("passkeySignResponse"));
        piContext.setPasskeyRegistration(request.getParameter("passkeyRegistration"));
        piContext.setPasskeyRegistrationResponse(request.getParameter("passkeyRegistrationResponse"));
        piContext.setPasskeyChallenge(request.getParameter("passkeyChallenge"));
        piContext.setOrigin(request.getParameter("origin"));
        piContext.setFormErrorMessage(request.getParameter("errorMessage"));
        // Capture the "remember this device" checkbox on every submit so its state survives form
        // reloads (e.g. push polling or a mistyped OTP). Read back at the success point below.
        piContext.setRememberMe("1".equals(request.getParameter("pidea_remember_me")));

        String standalone = request.getParameter("standalone");
        if (StringUtil.isNotBlank(standalone))
        {
            piContext.setStandalone(standalone);
        }
        // Opt-in params: request_persistent_cookie=1 when the box was ticked (and not standalone).
        Map<String, String> rememberParams = rememberMeParams(piContext);
        // Attach X-API-Key (+ stored cookie) only when remember-me is in play — opt-in or a stored
        // cookie — so a bad/expired key can never 401 an ordinary login (no header = legacy path).
        if (rememberMeManager != null)
        {
            rememberMeManager.applyRequestData(headers, !rememberParams.isEmpty());
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
                    if (piResponse.authenticationSuccessful())
                    {
                        if (StringUtil.isNotBlank(piResponse.username))
                        {
                            piContext.setUsername(piResponse.username);
                        }
                        finalizeAuthentication(profileRequestContext, piContext);
                        return;
                    }
                    else if (piResponse.authentication == AuthenticationStatus.REJECT)
                    {
                        LOGGER.error("{} Passkey authentication rejected!", this.getLogPrefix());
                        piContext.setFormErrorMessage("Passkey authentication rejected!");
                        piContext.setMode("otp");
                        ActionSupport.buildEvent(profileRequestContext, "reload");
                        return;
                    }
                    else if (piResponse.error != null)
                    {
                        LOGGER.error("{} Passkey authentication error: {}!", this.getLogPrefix(), piResponse.error.message);
                        piContext.setFormErrorMessage(piResponse.error.message);
                        ActionSupport.buildEvent(profileRequestContext, "reload");
                        return;
                    }
                }
            }
        }
        // Passkey login requested: Get a challenge and return
        if ("1".equals(request.getParameter("passkeyLoginRequested")))
        {
            PIResponse response = privacyIDEA.validateInitialize("passkey");
            if (StringUtil.isNotBlank(response.passkeyChallenge))
            {
                // /validate/initialize puts the prompt at detail.passkey.message, parsed into
                // response.passkeyMessage. detail.message is empty for that shape. Fall back to
                // response.message in case future server versions populate the top-level field.
                String passkeyPrompt = StringUtil.isNotBlank(response.passkeyMessage) ? response.passkeyMessage : response.message;
                piContext.setPasskeyMessage(passkeyPrompt);
                piContext.setPasskeyChallenge(response.passkeyChallenge);
                piContext.setMode("passkey");
                piContext.setPasskeyTransactionID(response.transactionID);

                // If the click originated from the username/password form's "Sign in with Passkey"
                // button, Spring Web Flow set _eventId_passkey on the request. In that case re-render
                // the same username form (with auto-triggered passkey JS) instead of jumping to the
                // second-step view — the user only sees the OS passkey dialog, not a UI transition.
                if (request.getParameterMap().containsKey("_eventId_passkey"))
                {
                    ActionSupport.buildEvent(profileRequestContext, "reloadUsernameForm");
                }
                else
                {
                    ActionSupport.buildEvent(profileRequestContext, "reload");
                }
                return;
            }
        }
        // Passkey login cancelled: Remove the challenge and passkey transaction ID
        if ("1".equals(request.getParameter("passkeyLoginCancelled")))
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
                else if (response.authenticationSuccessful())
                {
                    piContext.setPasskeyRegistration("");
                    finalizeAuthentication(profileRequestContext, piContext);
                    return;
                }
            }
        }

        if ("1".equals(request.getParameter("silentModeChange")))
        {
            ActionSupport.buildEvent(profileRequestContext, "reload");
            return;
        }

        // User declined an optional enroll-via-multichallenge offer: notify the server and finish.
        if ("1".equals(request.getParameter("cancelEnrollment")))
        {
            if (debug)
            {
                LOGGER.info("{} User declined optional enroll-via-multichallenge. Cancelling enrollment for transaction '{}'.",
                            this.getLogPrefix(), piContext.getTransactionID());
            }
            privacyIDEA.validateCheckCancelEnrollment(piContext.getTransactionID(), headers);
            // Primary auth already succeeded (otherwise no enroll-via-multichallenge offer would exist).
            // Use finalizeAuthentication so the standalone path still populates UsernameContext.
            finalizeAuthentication(profileRequestContext, piContext);
            return;
        }
        else if ("push".equals(piContext.getMode()))
        {
            // In push mode, poll for the transaction id to see if the challenge has been answered
            ChallengeStatus pollTransStatus = privacyIDEA.pollTransaction(piContext.getTransactionID());
            if (pollTransStatus == ChallengeStatus.accept)
            {
                // If the challenge has been answered, finalize with a call to validate check
                piResponse = privacyIDEA.validateCheck(piContext.getUsername(), "", piContext.getTransactionID(), rememberParams, headers);
                piContext.setMode("otp");
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
                                                               rememberParams,
                                                               headers);
            }
        }
        else if ("otp".equals(piContext.getMode()))
        {
            String otp = request.getParameter("otp");
            if (StringUtil.isNotBlank(otp))
            {
                piResponse = privacyIDEA.validateCheck(piContext.getUsername(), otp, piContext.getTransactionID(), rememberParams, headers);
            }
            else
            {
                if (debug)
                {
                    LOGGER.info("{} Cannot send OTP because the otp parameter is null or blank!", this.getLogPrefix());
                }
                // Without an explicit event the action-state falls through to its default outcome
                // (treated as `success` by Spring Web Flow → flow-state "proceed"), which would
                // finalize auth despite no OTP being submitted. Force a reload instead.
                ActionSupport.buildEvent(profileRequestContext, "reload");
                return;
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
            // Store/rotate/clear the IdP-domain remember-device cookie from privacyIDEA's Set-Cookie.
            if (rememberMeManager != null)
            {
                rememberMeManager.relayResponse(piResponse);
            }
            extractMessage(piResponse);

            if (piResponse.error != null)
            {
                LOGGER.error("{} privacyIDEA server error: {}!", this.getLogPrefix(), piResponse.error.message);
                piContext.setFormErrorMessage(piResponse.error.message);
                ActionSupport.buildEvent(profileRequestContext, "reload");
            }
            else if (piResponse.hasChallenges())
            {
                if (debug)
                {
                    LOGGER.info("{} Challenge triggered, building form...", this.getLogPrefix());
                }
                extractChallengeData(piResponse);
                ActionSupport.buildEvent(profileRequestContext, "reload");
            }
            else if (piResponse.authenticationSuccessful())
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
        if (StringUtil.isNotBlank(piContext.getStandalone()) && "1".equals(piContext.getStandalone()))
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