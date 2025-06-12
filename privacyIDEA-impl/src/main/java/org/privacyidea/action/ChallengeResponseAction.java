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

import jakarta.servlet.http.HttpServletRequest;
import net.shibboleth.idp.Version;
import net.shibboleth.idp.authn.context.AuthenticationContext;
import net.shibboleth.idp.profile.AbstractProfileAction;
import org.opensaml.messaging.context.navigate.ChildContextLookup;
import org.opensaml.profile.action.ActionSupport;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.Challenge;
import org.privacyidea.IPILogger;
import org.privacyidea.PIResponse;
import org.privacyidea.PrivacyIDEA;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIFormContext;
import org.privacyidea.context.PIServerConfigContext;
import org.privacyidea.context.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChallengeResponseAction extends AbstractProfileAction implements IPILogger
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ChallengeResponseAction.class);
    private PIServerConfigContext piServerConfigContext;
    private PIContext piContext;
    private PIFormContext piFormContext;
    protected PrivacyIDEA privacyIDEA;
    protected boolean debug = false;
    @Nonnull
    private final Function<ProfileRequestContext, PIContext> piContextLookupStrategy = (
            new ChildContextLookup(PIContext.class, false)).compose(
            new ChildContextLookup(AuthenticationContext.class));
    @Nonnull
    private final Function<ProfileRequestContext, PIFormContext> piFormContextLookupStrategy = (
            new ChildContextLookup(PIFormContext.class, false)).compose(
            new ChildContextLookup(AuthenticationContext.class));
    @Nonnull
    private final Function<ProfileRequestContext, PIServerConfigContext> piServerConfigLookupStrategy = (
            new ChildContextLookup(PIServerConfigContext.class, false)).compose(
            new ChildContextLookup(AuthenticationContext.class));

    protected final boolean doPreExecute(@Nonnull ProfileRequestContext profileRequestContext)
    {
        if (super.doPreExecute(profileRequestContext))
        {
            piServerConfigContext = piServerConfigLookupStrategy.apply(profileRequestContext);
            if (piServerConfigContext == null)
            {
                LOGGER.error("{} Unable to create/access privacyIDEA server config context.", this.getLogPrefix());
                ActionSupport.buildEvent(profileRequestContext, "InvalidProfileContext");
                return false;
            }
            else
            {
                piContext = piContextLookupStrategy.apply(profileRequestContext);
                if (piContext == null)
                {
                    LOGGER.error("{} Unable to create/access privacyIDEA context.", this.getLogPrefix());
                    ActionSupport.buildEvent(profileRequestContext, "InvalidProfileContext");
                    return false;
                }
                else
                {
                    piFormContext = piFormContextLookupStrategy.apply(profileRequestContext);
                    if (piFormContext == null)
                    {
                        LOGGER.error("{} Unable to create/access privacyIDEA form context.", this.getLogPrefix());
                        ActionSupport.buildEvent(profileRequestContext, "InvalidProfileContext");
                        return false;
                    }
                    else
                    {
                        if (piServerConfigContext.getConfigParams().getDebug())
                        {
                            debug = piServerConfigContext.getConfigParams().getDebug();
                        }

                        if (privacyIDEA == null)
                        {
                            String shibbVersion = Version.getVersion();
                            String pluginVersion = org.privacyidea.Version.getVersion();
                            String userAgent = "privacyIDEA-Shibboleth/" + pluginVersion + " ShibbolethIdP/" + shibbVersion;

                            privacyIDEA = PrivacyIDEA.newBuilder(piServerConfigContext.getConfigParams().getServerURL(), userAgent)
                                                     .verifySSL(piServerConfigContext.getConfigParams().getVerifySSL())
                                                     .realm(piServerConfigContext.getConfigParams().getRealm())
                                                     .serviceAccount(piServerConfigContext.getConfigParams().getServiceName(),
                                                                     piServerConfigContext.getConfigParams().getServicePass())
                                                     .serviceRealm(piServerConfigContext.getConfigParams().getServiceRealm())
                                                     .logger(this)
                                                     .build();
                        }
                        return true;
                    }
                }
            }
        }
        else
        {
            return false;
        }
    }

    protected final void doExecute(@Nonnull ProfileRequestContext profileRequestContext)
    {
        this.doExecute(profileRequestContext, this.piContext, this.piServerConfigContext);
    }

    protected void doExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext,
                             @Nonnull PIServerConfigContext piServerConfigContext)
    {}

    /**
     * Extract message from server response, and save it in form context.
     *
     * @param piResponse server response
     */
    protected void extractMessage(@Nonnull PIResponse piResponse)
    {
        if (StringUtil.isNotBlank(piResponse.message))
        {
            piFormContext.setMessage(piResponse.message);
        }
    }

    /**
     * Extract challenge data from server response, and save it in form context.
     *
     * @param piResponse server response
     */
    protected void extractChallengeData(@Nonnull PIResponse piResponse)
    {
        if (StringUtil.isNotBlank(piResponse.transactionID))
        {
            piContext.setTransactionID(piResponse.transactionID);
        }
        if (StringUtil.isNotBlank(piResponse.preferredClientMode))
        {
            piContext.setMode(piResponse.preferredClientMode);
        }

        // WebAuthn
        if (piResponse.triggeredTokenTypes().contains("webauthn"))
        {
            piContext.setWebauthnSignRequest(piResponse.mergedSignRequest());
        }

        // Passkey
        if (StringUtil.isNotBlank(piResponse.passkeyRegistration) && StringUtil.isNotBlank(piResponse.serial))
        {
            piContext.setPasskeyRegistration(piResponse.passkeyRegistration);
            piContext.setPasskeyRegistrationSerial(piResponse.serial);
        }
        if (StringUtil.isNotBlank(piResponse.passkeyChallenge))
        {
            piContext.setPasskeyChallenge(piResponse.passkeyChallenge);
        }

        // Push
        piContext.setIsPushAvailable(piResponse.pushAvailable());
        if (piContext.getIsPushAvailable())
        {
            piFormContext.setPushMessage(piResponse.pushMessage());
        }

        // Check for the images
        for (Challenge c : piResponse.multiChallenge)
        {
            if ("poll".equals(c.getClientMode()))
            {
                piContext.setMode("push");
                piFormContext.setImagePush(c.getImage());
            }
            else if ("interactive".equals(c.getClientMode()))
            {
                piContext.setMode("otp");
                piFormContext.setImageOtp(c.getImage());
            }
            if ("webauthn".equals(c.getClientMode()))
            {
                piContext.setMode("webauthn");
                piFormContext.setImageWebauthn(c.getImage());
            }
        }
    }

    /**
     * Search for the configured headers in HttpServletRequest and return all found with their values.
     *
     * @param request http servlet request
     * @return headers to forward with their values
     */
    protected Map<String, String> getHeadersToForward(HttpServletRequest request)
    {
        Map<String, String> headersToForward = new LinkedHashMap<>();
        if (StringUtil.isNotBlank(piServerConfigContext.getConfigParams().getForwardHeaders()))
        {
            String cleanHeaders = piServerConfigContext.getConfigParams().getForwardHeaders().replaceAll(" ", "");
            List<String> headersList = List.of(cleanHeaders.split(","));

            for (String headerName : headersList.stream().distinct().collect(Collectors.toList()))
            {
                List<String> headerValues = new ArrayList<>();
                Enumeration<String> e = request.getHeaders(headerName);
                if (e != null)
                {
                    while (e.hasMoreElements())
                    {
                        headerValues.add(e.nextElement());
                    }
                }

                if (!headerValues.isEmpty())
                {
                    String temp = String.join(",", headerValues);
                    headersToForward.put(headerName, temp);
                }
                else
                {
                    LOGGER.info("{} No values for header \"{}\" found.", this.getLogPrefix(), headerName);
                }
            }
        }
        return headersToForward;
    }

    // Logger implementation
    @Override
    public void log(String message)
    {
        if (debug)
        {
            LOGGER.info(message);
        }
    }

    @Override
    public void error(String message)
    {
        if (debug)
        {
            LOGGER.error(message);
        }
    }

    @Override
    public void log(Throwable throwable)
    {
        if (debug)
        {
            LOGGER.info(String.valueOf(throwable));
        }
    }

    @Override
    public void error(Throwable throwable)
    {
        if (debug)
        {
            LOGGER.error(String.valueOf(throwable));
        }
    }
}