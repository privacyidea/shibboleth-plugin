package org.privacyidea;

import java.io.IOException;
import net.shibboleth.idp.plugin.PluginException;
import net.shibboleth.idp.plugin.PropertyDrivenIdPPlugin;

/**
 * privacyIDEA MFA plugin for the Shibboleth IdP.
 */
public class PrivacyIDEAPlugin extends PropertyDrivenIdPPlugin
{
    /**
     * Constructor.
     *
     * @throws IOException     if properties can't be loaded
     * @throws PluginException if another error occurs
     */
    public PrivacyIDEAPlugin() throws IOException, PluginException
    {
        super(PrivacyIDEAPlugin.class);
    }
}