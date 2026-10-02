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
import net.shibboleth.idp.authn.AuthenticationResult;
import net.shibboleth.idp.authn.context.AuthenticationContext;
import net.shibboleth.idp.authn.context.MultiFactorAuthenticationContext;
import net.shibboleth.idp.authn.principal.UsernamePrincipal;
import net.shibboleth.idp.profile.AbstractProfileAction;
import org.opensaml.messaging.context.navigate.ChildContextLookup;
import org.opensaml.profile.action.ActionSupport;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.Challenge;
import org.privacyidea.PIResponse;
import org.privacyidea.PrivacyIDEA;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIFormContext;
import org.privacyidea.context.PIServerConfigContext;
import org.privacyidea.context.RememberMeManager;
import org.privacyidea.context.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChallengeResponseAction extends AbstractProfileAction
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ChallengeResponseAction.class);
    private PIServerConfigContext piServerConfigContext;
    private PIContext piContext;
    protected PIFormContext piFormContext;
    protected PrivacyIDEA privacyIDEA;
    protected boolean debug = false;
    // Remember-me ("trust this device") manager, shared with InitializePIContext via Spring.
    @Nullable
    protected RememberMeManager rememberMeManager;
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

    @Override
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
                            LOGGER.error("{} Shared privacyIDEA client is not available.", this.getLogPrefix());
                            ActionSupport.buildEvent(profileRequestContext, "InvalidProfileContext");
                            return false;
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

    @Override
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
        if (StringUtil.isNotBlank(piResponse.enrollmentLink))
        {
            piFormContext.setEnrollmentLink(piResponse.enrollmentLink);
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
            // PIN-triggered passkey (PasskeyAPITest::test_05_trigger_with_pin): server returns
            // preferred_client_mode=webauthn alongside type=passkey. Pin the mode to "passkey"
            // so the view renders the passkey path and pi-main.js doesn't fire doWebAuthn() with
            // an empty webauthnSignRequest. Also propagate the transaction id into
            // passkeyTransactionID because validateCheckPasskey() reads from there.
            piContext.setMode("passkey");
            if (StringUtil.isNotBlank(piResponse.transactionID))
            {
                piContext.setPasskeyTransactionID(piResponse.transactionID);
            }
            if (StringUtil.isNotBlank(piResponse.passkeyMessage))
            {
                piContext.setPasskeyMessage(piResponse.passkeyMessage);
            }
        }

        // Push
        piContext.setIsPushAvailable(piResponse.pushAvailable());
        if (piContext.isPushAvailable())
        {
            piFormContext.setPushMessage(piResponse.pushMessage());
        }

        // Carry the enroll-via-multichallenge flags through to the form. Driven by the server's own
        // enroll_via_multichallenge / _optional fields (not derived from an image being present), so a
        // passkey-registration enrollment — which carries passkey_registration but no QR image — is also
        // recognised and the optional "Not Now" button is offered.
        piFormContext.setEnrollViaMultichallenge(piResponse.isEnrollViaMultichallenge);
        piFormContext.setEnrollViaMultichallengeOptional(piResponse.isEnrollViaMultichallengeOptional);

        // Check for the images
        for (Challenge c : piResponse.multiChallenge)
        {
            if ("poll".equals(c.getClientMode()))
            {
                if (StringUtil.isNotBlank(c.getImage()))
                {
                    piFormContext.setImagePush(c.getImage());
                    //todo Workaround to show the push image by enrollment via challenge. Waiting for an update of privacyIDEA response.
                    // Waiting for updating of privacyidea that clearly indicates enroll_via_multichallenge challenges.
                    piContext.setMode("push");
                }
            }
            else if ("interactive".equals(c.getClientMode()))
            {
                piFormContext.setImageOtp(c.getImage());
            }
            if ("webauthn".equals(c.getClientMode()))
            {
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

        // Always forward Accept-Language so privacyIDEA can localize its responses.
        String acceptLanguage = request.getHeader("Accept-Language");
        if (StringUtil.isNotBlank(acceptLanguage))
        {
            headersToForward.put("Accept-Language", acceptLanguage);
        }

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

    /**
     * Build the {@code request_persistent_cookie} opt-in parameters for a {@code /validate/check}
     * call: {@code request_persistent_cookie=1} when remember-me was actually offered in this run
     * ({@link PIFormContext#isRememberMeEnabled()} — usable feature AND a fresh first factor to trust)
     * and the user ticked the "remember this device" box. Otherwise an empty map, so nothing changes for
     * the normal flow. Gating on the form-context flag (not the {@code standalone} request param) means a
     * cookie is never issued when privacyIDEA is the first/only factor (standalone or passkey-only),
     * mirroring the recognition/skip gate in {@link org.privacyidea.action.InitializePIContext}.
     *
     * @param piContext the current privacyIDEA context (source of the opt-in flag)
     * @return additional request parameters (possibly empty, never null)
     */
    @Nonnull
    protected Map<String, String> rememberMeParams(@Nonnull PIContext piContext)
    {
        Map<String, String> params = new LinkedHashMap<>();
        if (piFormContext != null && piFormContext.isRememberMeEnabled() && piContext.isRememberMe())
        {
            params.put("request_persistent_cookie", "1");
        }
        return params;
    }

    /**
     * Fix the user and the standalone flag for this run from server-side state, so the submitted
     * {@code username} and {@code standalone} form fields cannot change which user privacyIDEA validates
     * against, nor how the result is asserted.
     * <p>
     * When a preceding sub-flow in this MFA run produced a result (e.g. {@code authn/Password}), privacyIDEA
     * is a second factor: the user is that result's principal and the flag is cleared, so the token is always
     * checked for — and the login completes as — the identity the first factor established. The submitted
     * field is ignored. When there is no such result, privacyIDEA is the first/only factor (standalone): the
     * flag is set and the caller supplies the user from its own username form.
     *
     * @param profileRequestContext the current profile request context
     * @param piContext             the current privacyIDEA context (updated in place)
     * @return {@code true} if privacyIDEA is the first/only factor (standalone), {@code false} otherwise
     */
    protected boolean applyAuthoritativeIdentity(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext)
    {
        if (hasFreshAuthenticationResult(profileRequestContext))
        {
            String freshUser = freshResultUsername(profileRequestContext);
            if (StringUtil.isNotBlank(freshUser))
            {
                piContext.setUsername(freshUser);
            }
            piContext.setStandalone("");
            return false;
        }
        piContext.setStandalone("1");
        return true;
    }

    /**
     * @return {@code true} if a preceding sub-flow in this MFA run produced a result (privacyIDEA is a
     * second factor), {@code false} if privacyIDEA is the first/only factor.
     */
    protected boolean hasFreshAuthenticationResult(@Nonnull ProfileRequestContext profileRequestContext)
    {
        MultiFactorAuthenticationContext mfaContext = getMfaContext(profileRequestContext);
        return mfaContext != null && !mfaContext.getActiveResults().isEmpty();
    }

    /**
     * @return the username of the first active result that carries a {@link UsernamePrincipal} (the identity
     * the preceding factor authenticated), or {@code null} if none can be determined.
     */
    @Nullable
    protected String freshResultUsername(@Nonnull ProfileRequestContext profileRequestContext)
    {
        MultiFactorAuthenticationContext mfaContext = getMfaContext(profileRequestContext);
        if (mfaContext == null)
        {
            return null;
        }
        for (AuthenticationResult result : mfaContext.getActiveResults().values())
        {
            Set<UsernamePrincipal> principals = result.getSubject().getPrincipals(UsernamePrincipal.class);
            if (!principals.isEmpty())
            {
                return principals.iterator().next().getName();
            }
        }
        return null;
    }

    @Nullable
    private MultiFactorAuthenticationContext getMfaContext(@Nonnull ProfileRequestContext profileRequestContext)
    {
        AuthenticationContext authenticationContext = profileRequestContext.getSubcontext(AuthenticationContext.class);
        return authenticationContext == null ? null : authenticationContext.getSubcontext(MultiFactorAuthenticationContext.class);
    }

    /**
     * Determine the browser origin for a WebAuthn / passkey ceremony from the request itself rather than a
     * form field: the {@code Origin} header the browser sends, and if that is absent the scheme/host/port the
     * request arrived on. privacyIDEA checks this against the credential, so it must reflect the real page
     * origin and not a submitted value.
     *
     * @param request the current request
     * @return the origin, or {@code null} if it cannot be determined
     */
    @Nullable
    protected String resolveOrigin(@Nonnull HttpServletRequest request)
    {
        String origin = request.getHeader("Origin");
        if (StringUtil.isNotBlank(origin))
        {
            return origin;
        }
        String scheme = request.getScheme();
        String host = request.getServerName();
        if (StringUtil.isBlank(scheme) || StringUtil.isBlank(host))
        {
            return null;
        }
        int port = request.getServerPort();
        boolean defaultPort = ("https".equalsIgnoreCase(scheme) && port == 443)
                || ("http".equalsIgnoreCase(scheme) && port == 80);
        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }

    // Spring bean property setters
    public void setRememberMeManager(@Nullable RememberMeManager rememberMeManager) {this.rememberMeManager = rememberMeManager;}

    /** Inject the shared privacyIDEA client (singleton bean, built once per flow context). */
    public void setPrivacyIDEA(PrivacyIDEA privacyIDEA) {this.privacyIDEA = privacyIDEA;}
}