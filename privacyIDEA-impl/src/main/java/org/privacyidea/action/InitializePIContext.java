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

import net.shibboleth.idp.authn.AbstractAuthenticationAction;
import net.shibboleth.idp.authn.context.AuthenticationContext;
import net.shibboleth.idp.authn.context.MultiFactorAuthenticationContext;
import net.shibboleth.idp.session.context.navigate.CanonicalUsernameLookupStrategy;
import org.jetbrains.annotations.NotNull;
import org.opensaml.profile.action.ActionSupport;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.context.Config;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIFormContext;
import org.privacyidea.context.PIServerConfigContext;
import org.privacyidea.context.StringUtil;
import org.privacyidea.context.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Function;

public class InitializePIContext extends AbstractAuthenticationAction
{
    private static final Logger log = LoggerFactory.getLogger(InitializePIContext.class);
    @Nonnull
    private final Function<ProfileRequestContext, String> usernameLookupStrategy;
    private String serverURL;
    @Nullable
    private String realm;
    private boolean verifySSL;
    @Nullable
    private String defaultMessage;
    private String authenticationFlow;
    @Nullable
    private String serviceName;
    @Nullable
    private String servicePass;
    @Nullable
    private String serviceRealm;
    @Nullable
    private String staticPass;
    @Nullable
    private String forwardHeaders;
    @Nullable
    private String otpFieldHint;
    @Nullable
    private String otpLength;
    @Nullable
    private String pluginVersion;
    @Nullable
    private String pollingInterval;
    private boolean pollInBrowser;
    private boolean disablePasskey;
    @Nullable
    private String pollInBrowserUrl;
    private boolean debug;
    private boolean skipFirstStep = true;

    public InitializePIContext()
    {
        usernameLookupStrategy = new CanonicalUsernameLookupStrategy();
    }

    @Override
    protected void doExecute(@NotNull ProfileRequestContext profileRequestContext, @NotNull AuthenticationContext authenticationContext)
    {
        authenticationContext.removeSubcontext(PIContext.class);
        authenticationContext.removeSubcontext(PIServerConfigContext.class);

        PIServerConfigContext piServerConfigContext = getConfigBaseContext();
        log.info("{} Create PIServerConfigContext {}", this.getLogPrefix(), piServerConfigContext);
        authenticationContext.addSubcontext(piServerConfigContext);

        User user = getUser(profileRequestContext);
        PIContext piContext = new PIContext(user, pluginVersion);
        log.info("{} Create PIContext {}", this.getLogPrefix(), piContext);
        authenticationContext.addSubcontext(piContext);

        PIFormContext piFormContext = new PIFormContext(defaultMessage, otpFieldHint, getOtpLength(),
                                                        pollingInterval, pollInBrowser, pollInBrowserUrl, disablePasskey);
        log.info("{} Create PIFormContext {}", this.getLogPrefix(), piFormContext);
        authenticationContext.addSubcontext(piFormContext);
        if (user == null)
        {
            log.info("{} No principal name available. Displaying username-password-form.", getLogPrefix());
            ActionSupport.buildEvent(profileRequestContext, "displayUsernamePasswordForm");
        }
        else if (!skipFirstStep)
        {
            log.info("{} Principal name '{}' available but skip_first_step=false. Displaying username-password-form with prefilled username.", getLogPrefix(), user.getUsername());
            ActionSupport.buildEvent(profileRequestContext, "displayUsernamePasswordForm");
        }
        else if (!hasFreshAuthenticationResult(authenticationContext))
        {
            log.info("{} Principal name '{}' available from CanonicalUsernameLookupStrategy but no prior authentication flow produced a fresh result in this MFA run. Displaying username-password-form with prefilled username.",
                     getLogPrefix(), user.getUsername());
            ActionSupport.buildEvent(profileRequestContext, "displayUsernamePasswordForm");
        }
        else
        {
            log.info("{} Principal name '{}' already available via CanonicalUsernameLookupStrategy. Skipping username-password-form.", getLogPrefix(), user.getUsername());
        }
    }

    private boolean hasFreshAuthenticationResult(@Nonnull AuthenticationContext authenticationContext)
    {
        MultiFactorAuthenticationContext mfaCtx = authenticationContext.getSubcontext(MultiFactorAuthenticationContext.class);
        return mfaCtx != null && !mfaCtx.getActiveResults().isEmpty();
    }

    @Nullable
    private User getUser(@Nonnull ProfileRequestContext profileRequestContext)
    {
        String collectedUser = usernameLookupStrategy.apply(profileRequestContext);
        if (StringUtil.isBlank(collectedUser))
        {
            return null;
        }
        else
        {
            return new User(collectedUser);
        }
    }

    @NotNull
    private PIServerConfigContext getConfigBaseContext()
    {
        String authenticationFlow;
        String staticPass = null;
        if ("triggerChallenge".equals(this.authenticationFlow))
        {
            authenticationFlow = "triggerChallenge";
        }
        else if ("sendStaticPass".equals(this.authenticationFlow))
        {
            authenticationFlow = "sendStaticPass";
            if (this.staticPass != null)
            {
                staticPass = this.staticPass;
            }
        }
        else
        {
            authenticationFlow = "default";
        }
        Config
                configParams =
                new Config(serverURL,
                           realm,
                           verifySSL,
                           authenticationFlow,
                           serviceName,
                           servicePass,
                           serviceRealm,
                           staticPass,
                           pollInBrowser,
                           disablePasskey,
                           forwardHeaders,
                           otpLength,
                           debug);
        return new PIServerConfigContext(configParams);
    }

    @Nullable
    private Integer getOtpLength()
    {
        if (otpLength != null)
        {
            try
            {
                return Integer.parseInt(otpLength);
            }
            catch (NumberFormatException e)
            {
                log.info("{} Config option \"otp_length\": Wrong format. Only digits allowed.", getLogPrefix());
            }
        }
        return null;
    }

    // Spring bean property setters
    public void setServerURL(@Nonnull String serverURL) {this.serverURL = serverURL;}

    public void setRealm(@Nullable String realm)                          {this.realm = realm;}

    public void setVerifySSL(boolean verifySSL)                           {this.verifySSL = verifySSL;}

    public void setDefaultMessage(@Nullable String defaultMessage)        {this.defaultMessage = defaultMessage;}

    public void setOtpFieldHint(@Nullable String otpFieldHint)            {this.otpFieldHint = otpFieldHint;}

    public void setAuthenticationFlow(@Nonnull String authenticationFlow) {this.authenticationFlow = authenticationFlow;}

    public void setServiceName(@Nullable String serviceName)              {this.serviceName = serviceName;}

    public void setServicePass(@Nullable String servicePass)              {this.servicePass = servicePass;}

    public void setServiceRealm(@Nullable String serviceRealm)            {this.serviceRealm = serviceRealm;}

    public void setStaticPass(@Nullable String staticPass)                {this.staticPass = staticPass;}

    public void setForwardHeaders(@Nullable String forwardHeaders)        {this.forwardHeaders = forwardHeaders;}

    public void setOtpLength(@Nullable String otpLength)                  {this.otpLength = otpLength;}

    public void setPollingInterval(@Nullable String pollingInterval)      {this.pollingInterval = pollingInterval;}

    public void setPollInBrowser(boolean pollInBrowser)                   {this.pollInBrowser = pollInBrowser;}

    public void setPollInBrowserUrl(@Nullable String pollInBrowserUrl)    {this.pollInBrowserUrl = pollInBrowserUrl;}

    public void setDisablePasskey(boolean disablePasskey)                 {this.disablePasskey = disablePasskey;}

    public void setPluginVersion(@Nullable String pluginVersion)          {this.pluginVersion = pluginVersion;}

    public void setDebug(boolean debug)                                   {this.debug = debug;}

    public void setSkipFirstStep(boolean skipFirstStep)                   {this.skipFirstStep = skipFirstStep;}
}