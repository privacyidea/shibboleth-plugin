package org.privacyidea.action;

import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.shibboleth.idp.authn.AbstractAuthenticationAction;
import net.shibboleth.idp.authn.context.AuthenticationContext;
import net.shibboleth.idp.session.context.navigate.CanonicalUsernameLookupStrategy;
import org.jetbrains.annotations.NotNull;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.context.Config;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIFormContext;
import org.privacyidea.context.PIServerConfigContext;
import org.privacyidea.context.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

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
    private boolean triggerChallenge;
    @Nullable
    private String serviceName;
    @Nullable
    private String servicePass;
    @Nullable
    private String serviceRealm;
    @Nullable
    private String forwardHeaders;
    @Nullable
    private String otpFieldHint;
    @Nullable
    private String otpLength;
    @Nullable
    private String pluginVersion;
    private boolean debug;

    public InitializePIContext()
    {
        usernameLookupStrategy = new CanonicalUsernameLookupStrategy();
    }

    @Override
    protected void doExecute(@NotNull ProfileRequestContext profileRequestContext, @NotNull AuthenticationContext authenticationContext)
    {
        authenticationContext.removeSubcontext(PIContext.class);
        authenticationContext.removeSubcontext(PIServerConfigContext.class);

        User user = getUser(profileRequestContext);
        if (user == null)
        {
            log.info("{} No principal name available.", getLogPrefix());
        }
        else
        {
            Config configParams = new Config(serverURL, realm, verifySSL, triggerChallenge, serviceName, servicePass, serviceRealm, forwardHeaders, otpLength, debug);
            PIServerConfigContext piServerConfigContext = new PIServerConfigContext(configParams);
            log.info("{} Create PIServerConfigContext {}", this.getLogPrefix(), piServerConfigContext);
            authenticationContext.addSubcontext(piServerConfigContext);

            PIContext piContext = new PIContext(user, pluginVersion);
            log.info("{} Create PIContext {}", this.getLogPrefix(), piContext);
            authenticationContext.addSubcontext(piContext);

            PIFormContext piFormContext;
            if (otpLength != null)
            {
                try
                {
                    int otpLengthToInt = Integer.parseInt(otpLength);
                    piFormContext = new PIFormContext(defaultMessage, otpFieldHint, otpLengthToInt);
                }
                catch (NumberFormatException e)
                {
                    log.info("{} Config option \"otp_length\": Wrong format. Only digits allowed.", getLogPrefix());
                    piFormContext = new PIFormContext(defaultMessage, otpFieldHint, null);
                }
            }
            else
            {
                piFormContext = new PIFormContext(defaultMessage, otpFieldHint, null);
            }
            log.info("{} Create PIFormContext {}", this.getLogPrefix(), piFormContext);
            authenticationContext.addSubcontext(piFormContext);
        }
    }

    @Nullable
    private User getUser(@Nonnull ProfileRequestContext profileRequestContext)
    {
        String collectedUser = usernameLookupStrategy.apply(profileRequestContext);
        if (!StringUtils.hasText(collectedUser))
        {
            return null;
        }
        else
        {
            return new User(collectedUser);
        }
    }

    // Spring bean property setters
    public void setServerURL(@Nonnull String serverURL) {this.serverURL = serverURL;}

    public void setRealm(@Nullable String realm) {this.realm = realm;}

    public void setVerifySSL(boolean verifySSL) {this.verifySSL = verifySSL;}

    public void setDefaultMessage(@Nullable String defaultMessage) {this.defaultMessage = defaultMessage;}

    public void setOtpFieldHint(@Nullable String otpFieldHint) {this.otpFieldHint = otpFieldHint;}

    public void setTriggerChallenge(boolean triggerChallenge) {this.triggerChallenge = triggerChallenge;}

    public void setServiceName(@Nullable String serviceName) {this.serviceName = serviceName;}

    public void setServicePass(@Nullable String servicePass) {this.servicePass = servicePass;}

    public void setServiceRealm(@Nullable String serviceRealm) {this.serviceRealm = serviceRealm;}

    public void setForwardHeaders(@Nullable String forwardHeaders) {this.forwardHeaders = forwardHeaders;}

    public void setOtpLength(@Nullable String otpLength) {this.otpLength = otpLength;}

    public void setPluginVersion(@Nullable String pluginVersion) {this.pluginVersion = pluginVersion;}

    public void setDebug(boolean debug) {this.debug = debug;}
}