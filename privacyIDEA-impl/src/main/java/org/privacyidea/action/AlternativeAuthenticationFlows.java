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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;

import net.shibboleth.idp.authn.context.UsernameContext;
import org.opensaml.profile.action.ActionSupport;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.PIResponse;
import org.privacyidea.TokenInfo;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIServerConfigContext;
import org.privacyidea.context.StringUtil;
import org.privacyidea.context.TokenListEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlternativeAuthenticationFlows extends ChallengeResponseAction
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AlternativeAuthenticationFlows.class);

    public AlternativeAuthenticationFlows() {}

    @Override
    protected final void doExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext, @Nonnull PIServerConfigContext piServerConfigContext)
    {
        HttpServletRequest request = Objects.requireNonNull(getHttpServletRequestSupplier()).get();
        
        String standalone = request.getParameter("standalone");
        if (StringUtil.isNotBlank(standalone))
        {
            piContext.setStandalone(standalone);
        }
        
        // The "username" param is present (possibly empty) when the user submits the username/password
        // form; it is absent (null) on flow paths that didn't go through the form (skip_first_step etc.).
        // If the form was submitted with a blank field, clear any prefilled username so a stale principal
        // can't be carried forward — the downstream isBlank guard then redirects to the username form.
        String username = request.getParameter("username");
        if (username != null)
        {
            if (StringUtil.isNotBlank(username))
            {
                piContext.setUsername(username);
            }
            else
            {
                piContext.clearUsername();
            }
        }

        // Reset any stale form error from a previous submission. Matches the pattern in
        // PrivacyIDEAAuthenticator, which reads the hidden "errorMessage" field (hardcoded "")
        // on every submit so the message only displays for the failing render.
        piContext.setFormErrorMessage(request.getParameter("errorMessage"));

        if ("triggerChallenge".equals(piServerConfigContext.getConfigParams().getAuthenticationFlow()))
        {
            if (debug)
            {
                LOGGER.info("{} Authentication flow - triggerChallenge.", this.getLogPrefix());
            }

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

                if (piResponse.hasChallenges())
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
        else if ("tokenSelection".equals(piServerConfigContext.getConfigParams().getAuthenticationFlow()))
        {
            if (debug)
            {
                LOGGER.info("{} Authentication flow - tokenSelection.", this.getLogPrefix());
            }
            // Fetch the user's tokens (service-account GET /token) and hand them to the view. If the list
            // cannot be retrieved (no service account / request failed), fall back to the plain OTP form
            // rather than failing the login.
            List<TokenInfo> tokenInfos = privacyIDEA.getTokenInfo(piContext.getUsername());
            if (tokenInfos == null)
            {
                LOGGER.warn("{} tokenSelection: could not retrieve the token list (service account missing or request failed); falling back to the OTP form.",
                            this.getLogPrefix());
            }
            else
            {
                List<TokenListEntry> entries = new ArrayList<>();
                for (TokenInfo token : tokenInfos)
                {
                    entries.add(new TokenListEntry(token.serial, token.tokenType, token.description,
                                                   token.info.get("last_auth"), token.active, token.revoked,
                                                   token.locked, token.rolloutState));
                }
                // Usable tokens first; unusable (revoked/locked/…) at the end so the user still sees them.
                entries.sort(Comparator.comparing(TokenListEntry::isUsable).reversed());
                piFormContext.setTokens(entries);
            }
        }
        else if ("sendStaticPass".equals(piServerConfigContext.getConfigParams().getAuthenticationFlow()))
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
                    
                    if (piResponse.authenticationSuccessful())
                    {
                        if (debug)
                        {
                            LOGGER.info("{} Authentication succeeded!", this.getLogPrefix());
                        }
                        if (StringUtil.isNotBlank(piContext.getStandalone()) && "1".equals(piContext.getStandalone()))
                        {
                            if (debug)
                            {
                                LOGGER.info("{} Standalone mode, setting username and building event...", this.getLogPrefix());
                                LOGGER.info("username: {}", piContext.getUsername());
                            }
                            UsernameContext userCtx = profileRequestContext.getSubcontext(UsernameContext.class, true);
                            Objects.requireNonNull(userCtx).setUsername(piContext.getUsername());
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

                    if (piResponse.hasChallenges())
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

            String otp = request.getParameter("otp");
            if (StringUtil.isBlank(piContext.getUsername()))
            {
                // Form submitted without a username — skip the /validate/check and send the user back
                // to the username/password form. checkAuthenticationFlow short-circuits on this event
                // before its hard-coded "proceed" evaluate runs.
                if (debug)
                {
                    LOGGER.info("{} No username available; redisplaying username/password form.", this.getLogPrefix());
                }
                piContext.setFormErrorMessage("Username is required.");
                ActionSupport.buildEvent(profileRequestContext, "redisplayUsernameForm");
                return;
            }
            if (StringUtil.isNotBlank(otp))
            {
                Map<String, String> headers = this.getHeadersToForward(request);
                PIResponse piResponse = privacyIDEA.validateCheck(piContext.getUsername(), otp, headers);

                if (piResponse == null)
                {
                    LOGGER.warn("{} No response from privacyIDEA server for validateCheck.", this.getLogPrefix());
                    return;
                }
                if (piResponse.error != null)
                {
                    LOGGER.error("{} privacyIDEA server error: {}!", this.getLogPrefix(), piResponse.error.message);
                    ActionSupport.buildEvent(profileRequestContext, "AuthenticationException");
                    return;
                }
                extractMessage(piResponse);

                if (piResponse.authenticationSuccessful())
                {
                    if ("1".equals(piContext.getStandalone()))
                    {
                        if (debug)
                        {
                            LOGGER.info("{} Standalone mode, setting username '{}' and building event...",
                                        this.getLogPrefix(), piContext.getUsername());
                        }
                        UsernameContext userCtx = profileRequestContext.getSubcontext(UsernameContext.class, true);
                        Objects.requireNonNull(userCtx).setUsername(piContext.getUsername());
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
                    return;
                }

                // Authentication not yet successful — extract any additional challenge for re-render.
                if (piResponse.hasChallenges())
                {
                    extractChallengeData(piResponse);
                }
            }
        }
    }
}