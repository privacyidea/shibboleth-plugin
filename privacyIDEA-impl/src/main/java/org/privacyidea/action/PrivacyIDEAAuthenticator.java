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
        // Attach the X-API-Key only for issuance (opt-in), so a bad/expired key can never 401 an
        // ordinary login (no header = anonymous/legacy path). The cookie is NOT sent here: /validate/check
        // no longer consumes it — recognition is the separate /validate/remember_device endpoint.
        if (rememberMeManager != null && !rememberParams.isEmpty())
        {
            rememberMeManager.addApiKey(headers);
        }

        // tokenSelection: the user clicked "Use" on a specific token row — trigger that token, then
        // re-render into the resulting challenge (push poll / WebAuthn / passkey). Only the Use button
        // sets these, and they reset to empty on every re-render, so this fires once per selection.
        String selectedType = request.getParameter("selectedType");
        String selectedSerial = request.getParameter("selectedSerial");
        if (StringUtil.isNotBlank(selectedType) && StringUtil.isNotBlank(selectedSerial))
        {
            triggerSelectedToken(profileRequestContext, piContext, selectedType, selectedSerial, headers);
            return;
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
                                                              rememberParams,
                                                              headers);
                if (piResponse != null)
                {
                    if (piResponse.authenticationSuccessful())
                    {
                        // Passkeys are usernameless: validateCheckPasskey resolves to whoever owns the
                        // credential. If this login already established an identity in a prior step (e.g.
                        // username+password, then "Sign in with Passkey" as the second factor), the passkey
                        // MUST resolve to that same user — otherwise the two factors would authenticate
                        // different people and the MFA binding would be meaningless (or bypassable).
                        String established = piContext.getUsername();
                        if (StringUtil.isNotBlank(established))
                        {
                            // privacyIDEA returns a bare username; the established (IdP canonical) principal
                            // may be realm/scope-qualified (e.g. user@realm, or a Windows-style domain
                            // prefix), so compare on the local part, case-insensitively (AD is
                            // case-insensitive).
                            if (StringUtil.isNotBlank(piResponse.username)
                                    && !localPart(established).equalsIgnoreCase(piResponse.username))
                            {
                                LOGGER.error("{} Passkey resolved to '{}' but the login was started as '{}'. Rejecting.",
                                             this.getLogPrefix(), piResponse.username, established);
                                piContext.setFormErrorMessage("Passkey does not match the signed-in user.");
                                piContext.setMode("otp");
                                ActionSupport.buildEvent(profileRequestContext, "reload");
                                return;
                            }
                            // Match: keep the established canonical principal. Do NOT overwrite it with the
                            // bare passkey username, which could break downstream c14n / attribute resolution.
                        }
                        else if (StringUtil.isNotBlank(piResponse.username))
                        {
                            // No prior identity (true usernameless / standalone passkey): adopt what the
                            // passkey resolved to.
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
     * Trigger the token the user picked in the tokenSelection list, then reload into its challenge:
     * a passkey uses {@code /validate/initialize} (usernameless challenge, like "Sign in with Passkey");
     * push / WebAuthn (and any other challenge token) use {@code /validate/triggerchallenge} scoped to the
     * serial. {@link #extractChallengeData}/{@link #extractMessage} set the mode, transaction id and
     * challenge data so the re-rendered form drives the right ceremony (push poll / WebAuthn / passkey).
     *
     * @param profileRequestContext the current profile request context
     * @param piContext             the current privacyIDEA context
     * @param type                  the selected token's type
     * @param serial                the selected token's serial
     * @param headers               headers to forward to privacyIDEA
     */
    private void triggerSelectedToken(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext,
                                      @Nonnull String type, @Nonnull String serial, @Nonnull Map<String, String> headers)
    {
        // Remember which row was picked so the re-rendered list can mark it and drive the right ceremony.
        // The type is authoritative — we set the mode explicitly per type rather than inferring it from the
        // response, because triggerchallenge for a user who also owns a passkey/webauthn token can sweep those
        // challenges into the response and flip the view into the wrong ceremony.
        piContext.setSelectedSerial(serial);
        piContext.setSelectedType(type);
        // Clear any previous ceremony's passkey challenge before setting up the new selection. Otherwise a
        // cancelled passkey (challenge still set) would survive into e.g. a following push selection and the
        // auto-run script would flip the client back into the passkey ceremony.
        piContext.setPasskeyChallenge("");
        piContext.setPasskeyMessage(null);
        piContext.setPasskeyTransactionID(null);
        if ("passkey".equalsIgnoreCase(type))
        {
            PIResponse response = privacyIDEA.validateInitialize("passkey");
            if (response != null && StringUtil.isNotBlank(response.passkeyChallenge))
            {
                piContext.setPasskeyMessage(StringUtil.isNotBlank(response.passkeyMessage) ? response.passkeyMessage : response.message);
                piContext.setPasskeyChallenge(response.passkeyChallenge);
                piContext.setMode("passkey");
                piContext.setPasskeyTransactionID(response.transactionID);
            }
            else
            {
                LOGGER.error("{} tokenSelection: could not initialize a passkey challenge.", this.getLogPrefix());
            }
        }
        else if ("push".equalsIgnoreCase(type))
        {
            // Send the push notification and enter poll mode in place. Only the transaction id / push message
            // are taken from the response — no extractChallengeData, so a co-triggered passkey/webauthn
            // challenge cannot hijack the mode.
            PIResponse response = privacyIDEA.triggerChallenges(piContext.getUsername(), Map.of("serial", serial), headers);
            if (response == null)
            {
                LOGGER.error("{} tokenSelection: triggering push token '{}' returned no response.", this.getLogPrefix(), serial);
            }
            else if (response.error != null)
            {
                LOGGER.error("{} tokenSelection: triggering push token '{}' failed: {}!", this.getLogPrefix(), serial, response.error.message);
                piContext.setFormErrorMessage(response.error.message);
            }
            else
            {
                if (StringUtil.isNotBlank(response.transactionID))
                {
                    piContext.setTransactionID(response.transactionID);
                }
                piContext.setIsPushAvailable(true);
                piFormContext.setPushMessage(response.pushMessage());
                piContext.setMode("push");
            }
        }
        else if ("webauthn".equalsIgnoreCase(type))
        {
            // Get the WebAuthn sign request and run the ceremony in place (like passkey, but via the WebAuthn
            // JS path since the challenge is encoded differently). Only the transaction id / sign request /
            // message are taken from the response — no extractChallengeData, so a co-triggered passkey
            // challenge cannot hijack the mode.
            PIResponse response = privacyIDEA.triggerChallenges(piContext.getUsername(), Map.of("serial", serial), headers);
            if (response == null)
            {
                LOGGER.error("{} tokenSelection: triggering WebAuthn token '{}' returned no response.", this.getLogPrefix(), serial);
            }
            else if (response.error != null)
            {
                LOGGER.error("{} tokenSelection: triggering WebAuthn token '{}' failed: {}!", this.getLogPrefix(), serial, response.error.message);
                piContext.setFormErrorMessage(response.error.message);
            }
            else
            {
                if (StringUtil.isNotBlank(response.transactionID))
                {
                    piContext.setTransactionID(response.transactionID);
                }
                piContext.setWebauthnSignRequest(response.mergedSignRequest());
                piContext.setMode("webauthn");
                extractMessage(response);
            }
        }
        else
        {
            // Any other challenge token: generic path (still hands off to the classic layout).
            PIResponse response = privacyIDEA.triggerChallenges(piContext.getUsername(), Map.of("serial", serial), headers);
            if (response == null)
            {
                LOGGER.error("{} tokenSelection: triggering token '{}' returned no response.", this.getLogPrefix(), serial);
            }
            else if (response.error != null)
            {
                LOGGER.error("{} tokenSelection: triggering token '{}' failed: {}!", this.getLogPrefix(), serial, response.error.message);
                piContext.setFormErrorMessage(response.error.message);
            }
            else
            {
                extractChallengeData(response);
                extractMessage(response);
            }
        }
        ActionSupport.buildEvent(profileRequestContext, "reload");
    }

    /**
     * Reduce a username to its bare local part for comparison: strip a Windows-style domain prefix
     * (everything up to and including a backslash) and an {@code @realm} suffix. Used to compare an IdP
     * canonical principal (which may be realm/scope-qualified) against the bare username privacyIDEA
     * returns for a passkey.
     *
     * @param username the username to normalize (must not be null)
     * @return the local part
     */
    // package-private for unit testing
    static String localPart(@Nonnull String username)
    {
        String result = username;
        int backslash = result.indexOf('\\');
        if (backslash >= 0)
        {
            result = result.substring(backslash + 1);
        }
        int at = result.indexOf('@');
        if (at >= 0)
        {
            result = result.substring(0, at);
        }
        return result;
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