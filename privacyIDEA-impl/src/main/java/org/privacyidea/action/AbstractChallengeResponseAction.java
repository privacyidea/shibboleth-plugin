package org.privacyidea.action;

import java.util.function.Function;
import javax.annotation.Nonnull;
import net.shibboleth.idp.authn.context.AuthenticationContext;
import net.shibboleth.idp.profile.AbstractProfileAction;
import org.opensaml.messaging.context.navigate.ChildContextLookup;
import org.opensaml.profile.action.ActionSupport;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.IPILogger;
import org.privacyidea.PIResponse;
import org.privacyidea.PrivacyIDEA;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIFormContext;
import org.privacyidea.context.PIServerConfigContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractChallengeResponseAction extends AbstractProfileAction implements IPILogger
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractChallengeResponseAction.class);
    private PIServerConfigContext piServerConfigContext;
    private PIContext piContext;
    private PIFormContext piFormContext;
    protected PrivacyIDEA privacyIDEA;
    protected boolean debug = false;
    @Nonnull
    private final Function<ProfileRequestContext, PIContext> piContextLookupStrategy = (new ChildContextLookup(PIContext.class, false)).compose(
            new ChildContextLookup(AuthenticationContext.class));
    @Nonnull
    private final Function<ProfileRequestContext, PIFormContext> piFormContextLookupStrategy = (new ChildContextLookup(PIFormContext.class, false)).compose(
            new ChildContextLookup(AuthenticationContext.class));
    @Nonnull
    private final Function<ProfileRequestContext, PIServerConfigContext> piServerConfigLookupStrategy = (new ChildContextLookup(PIServerConfigContext.class, false)).compose(
            new ChildContextLookup(AuthenticationContext.class));

    protected final boolean doPreExecute(@Nonnull ProfileRequestContext profileRequestContext)
    {
        if (super.doPreExecute(profileRequestContext))
        {
            piServerConfigContext = piServerConfigLookupStrategy.apply(profileRequestContext);
            if (piServerConfigContext == null)
            {
                LOGGER.error("{} Unable to create/access privacyIDEA server config context.", this.getLogPrefix());
                ActionSupport.buildEvent(profileRequestContext, "InvalidProfileContext");
                return false;
            }
            else
            {
                piContext = piContextLookupStrategy.apply(profileRequestContext);
                if (piContext == null)
                {
                    LOGGER.error("{} Unable to create/access privacyIDEA context.", this.getLogPrefix());
                    ActionSupport.buildEvent(profileRequestContext, "InvalidProfileContext");
                    return false;
                }
                else
                {
                    piFormContext = piFormContextLookupStrategy.apply(profileRequestContext);
                    if (piFormContext == null)
                    {
                        LOGGER.error("{} Unable to create/access privacyIDEA form context.", this.getLogPrefix());
                        ActionSupport.buildEvent(profileRequestContext, "InvalidProfileContext");
                        return false;
                    }
                    else
                    {
                        if (piServerConfigContext.getConfigParams().getDebug())
                        {
                            debug = piServerConfigContext.getConfigParams().getDebug();
                        }

                        if (privacyIDEA == null)
                        {
                            privacyIDEA = PrivacyIDEA.newBuilder(piServerConfigContext.getConfigParams().getServerURL(), "privacyIDEA-Shibboleth-Plugin")
                                                     .sslVerify(piServerConfigContext.getConfigParams().getVerifySSL())
                                                     .realm(piServerConfigContext.getConfigParams().getRealm())
                                                     .serviceAccount(piServerConfigContext.getConfigParams().getServiceName(),
                                                                     piServerConfigContext.getConfigParams().getServicePass())
                                                     .serviceRealm(piServerConfigContext.getConfigParams().getServiceRealm())
                                                     .logger(this)
                                                     .build();
                        }
                        return true;
                    }
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

    protected void doExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext, @Nonnull PIServerConfigContext piServerConfigContext) {}

    /**
     * Extract challenge data from server response, and save it in context.
     *
     * @param piResponse server response
     */
    protected void extractChallengeData(@Nonnull PIResponse piResponse)
    {
        if (piResponse.message != null && !piResponse.message.isEmpty())
        {
            piFormContext.setMessage(piResponse.message);
        }
        if (piResponse.transactionID != null && !piResponse.transactionID.isEmpty())
        {
            piContext.setTransactionID(piResponse.transactionID);
        }
    }

    // Logger implementation
    @Override
    public void log(String message)
    {
        if (debug)
        {
            LOGGER.info("PrivacyIDEA Client: " + message);
        }
    }

    @Override
    public void error(String message)
    {
        if (debug)
        {
            LOGGER.error("PrivacyIDEA Client: " + message);
        }
    }

    @Override
    public void log(Throwable throwable)
    {
        if (debug)
        {
            LOGGER.info("PrivacyIDEA Client: " + throwable);
        }
    }

    @Override
    public void error(Throwable throwable)
    {
        if (debug)
        {
            LOGGER.error("PrivacyIDEA Client: " + throwable);
        }
    }
}
