package org.privacyidea.context;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opensaml.messaging.context.BaseContext;

public class PIServerConfigContext extends BaseContext
{
    @Nonnull
    Config configParams;

    /**
     * Constructor
     */
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