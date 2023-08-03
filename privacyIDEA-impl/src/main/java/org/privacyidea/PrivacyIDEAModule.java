package org.privacyidea;

import java.io.IOException;
import net.shibboleth.idp.module.IdPModule;
import net.shibboleth.idp.module.ModuleException;
import net.shibboleth.idp.module.impl.PluginIdPModule;

/**
 * {@link IdPModule IdP Module} implementation.
 */
public class PrivacyIDEAModule extends PluginIdPModule
{
    /**
     * Constructor.
     *
     * @throws ModuleException on error
     * @throws IOException     on error
     */
    public PrivacyIDEAModule() throws IOException, ModuleException
    {
        super(PrivacyIDEAModule.class);
    }
}