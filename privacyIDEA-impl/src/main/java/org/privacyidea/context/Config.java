package org.privacyidea.context;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class Config
{
    @Nonnull
    private final String serverURL;
    @Nullable
    private final String realm;
    private final boolean verifySSL;
    private final boolean debug;
    @Nonnull
    private final String authenticationFlow;
    @Nullable
    private final String serviceName;
    @Nullable
    private final String servicePass;
    @Nullable
    private final String serviceRealm;
    @Nullable
    private final String staticPass;
    @Nullable
    private final String forwardHeaders;
    @Nullable
    private final String otpLength;

    public Config(@Nonnull String serverURL, @Nullable String realm, boolean verifySSL, @Nonnull String authenticationFlow,
                  @Nullable String serviceName, @Nullable String servicePass, @Nullable String serviceRealm,
                  @Nullable String staticPass, @Nullable String forwardHeaders, @Nullable String otpLength, boolean debug)
    {
        this.serverURL = serverURL;
        this.realm = realm;
        this.verifySSL = verifySSL;
        this.authenticationFlow = authenticationFlow;
        this.serviceName = serviceName;
        this.servicePass = servicePass;
        this.serviceRealm = serviceRealm;
        this.staticPass = staticPass;
        this.forwardHeaders = forwardHeaders;
        this.otpLength = otpLength;
        this.debug = debug;
    }

    // Config getters
    @Nonnull
    public String getServerURL()
    {
        return serverURL;
    }

    @Nullable
    public String getRealm()
    {
        return realm;
    }

    public boolean getVerifySSL()
    {
        return verifySSL;
    }

    @Nonnull
    public String getAuthenticationFlow()
    {
        return authenticationFlow;
    }

    @Nullable
    public String getServiceName()
    {
        return serviceName;
    }

    @Nullable
    public String getServicePass()
    {
        return servicePass;
    }

    @Nullable
    public String getServiceRealm()
    {
        return serviceRealm;
    }

    @Nullable
    public String getStaticPass()
    {
        return staticPass;
    }

    @Nullable
    public String getForwardHeaders()
    {
        return forwardHeaders;
    }

    @Nullable
    public String getOtpLength()
    {
        return otpLength;
    }

    public boolean getDebug()
    {
        return debug;
    }
}