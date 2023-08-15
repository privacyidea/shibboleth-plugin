package org.privacyidea.action;

import java.util.function.Function;
import javax.annotation.Nonnull;
import net.shibboleth.idp.authn.context.AuthenticationContext;
import net.shibboleth.idp.profile.AbstractProfileAction;
import org.opensaml.messaging.context.navigate.ChildContextLookup;
import org.opensaml.profile.action.ActionSupport;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.context.PIServerConfigContext;
import org.privacyidea.context.PIContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractChallengeResponseAction extends AbstractProfileAction
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractChallengeResponseAction.class);
    private PIServerConfigContext piServerConfigContext;
    private PIContext piContext;
    @Nonnull
    private final Function<ProfileRequestContext, PIContext> piContextLookupStrategy = (new ChildContextLookup(PIContext.class, false)).compose(
            new ChildContextLookup(AuthenticationContext.class));
    @Nonnull
    private final Function<ProfileRequestContext, PIServerConfigContext> piServerConfigLookupStrategy = (new ChildContextLookup(PIServerConfigContext.class, false)).compose(
            new ChildContextLookup(AuthenticationContext.class));

    protected final boolean doPreExecute(@Nonnull ProfileRequestContext profileRequestContext)
    {
        if (super.doPreExecute(profileRequestContext))
        {
            this.piServerConfigContext = this.piServerConfigLookupStrategy.apply(profileRequestContext);
            if (this.piServerConfigContext == null)
            {
                LOGGER.error("{} Unable to create/access privacyIDEA Server Config.", this.getLogPrefix());
                ActionSupport.buildEvent(profileRequestContext, "InvalidProfileContext");
                return false;
            }
            else
            {
                this.piContext = this.piContextLookupStrategy.apply(profileRequestContext);
                if (this.piContext == null)
                {
                    LOGGER.error("{} Unable to create/access privacyIDEA Context.", this.getLogPrefix());
                    ActionSupport.buildEvent(profileRequestContext, "InvalidProfileContext");
                    return false;
                }
                else
                {
                    return true;
                }
            }

        }
        else
        {
            return false;
        }
    }

    protected final void doExecute(@Nonnull ProfileRequestContext profileRequestContext)
    {
        this.doExecute(profileRequestContext, this.piContext, this.piServerConfigContext);
    }

    protected void doExecute(@Nonnull ProfileRequestContext profileRequestContext,
                             @Nonnull PIContext piContext,
                             @Nonnull PIServerConfigContext piServerConfigContext) {}
}
