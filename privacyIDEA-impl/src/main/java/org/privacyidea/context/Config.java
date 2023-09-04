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
    private final boolean triggerChallenge;
    @Nullable
    private final String serviceName;
    @Nullable
    private final String servicePass;
    @Nullable
    private final String serviceRealm;

    public Config(@Nonnull String serverURL, @Nullable String realm, boolean verifySSL, boolean triggerChallenge, @Nullable String serviceName, @Nullable String servicePass, @Nullable String serviceRealm, boolean debug)
    {
        this.serverURL = serverURL;
        this.realm = realm;
        this.verifySSL = verifySSL;
        this.triggerChallenge = triggerChallenge;
        this.serviceName = serviceName;
        this.servicePass = servicePass;
        this.serviceRealm = serviceRealm;
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
    public boolean getTriggerChallenge()
    {
        return triggerChallenge;
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
    public boolean getDebug()
    {
        return debug;
    }
}