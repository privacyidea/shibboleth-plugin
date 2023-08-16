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

    /**
     * Constructor
     */
    public PIServerConfigContext(@Nonnull String serverURL, @Nullable String realm, boolean verifySSL)
    {
        this.serverURL = serverURL;
        this.realm = realm;
        this.verifySSL = verifySSL;
    }

    // Getters
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
}