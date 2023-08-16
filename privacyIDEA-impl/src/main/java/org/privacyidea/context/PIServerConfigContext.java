package org.privacyidea.context;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opensaml.messaging.context.BaseContext;

public class PIServerConfigContext extends BaseContext
{
    @Nonnull
    private final String serverURL;
    @Nullable
    private final String realm;
    private final boolean verifySSL;
    private final boolean debug;

    /**
     * Constructor
     */
    public PIServerConfigContext(@Nonnull String serverURL, @Nullable String realm, boolean verifySSL, boolean debug)
    {
        this.serverURL = serverURL;
        this.realm = realm;
        this.verifySSL = verifySSL;
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
    public boolean getDebug()
    {
        return debug;
    }
}