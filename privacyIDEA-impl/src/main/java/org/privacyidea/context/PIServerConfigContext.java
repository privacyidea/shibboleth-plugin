package org.privacyidea.context;

import javax.annotation.Nonnull;
import org.opensaml.messaging.context.BaseContext;

public class PIServerConfigContext extends BaseContext
{
    @Nonnull
    Config configParams;

    public PIServerConfigContext(@Nonnull Config configParams)
    {
        this.configParams = configParams;
    }

    @Nonnull
    public Config getConfigParams()
    {
        return configParams;
    }
}