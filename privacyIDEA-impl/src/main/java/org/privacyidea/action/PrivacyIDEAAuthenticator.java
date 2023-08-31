package org.privacyidea.action;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import net.shibboleth.idp.authn.context.AuthenticationContext;
import org.opensaml.profile.action.ActionSupport;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.IPILogger;
import org.privacyidea.PIResponse;
import org.privacyidea.PrivacyIDEA;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIServerConfigContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrivacyIDEAAuthenticator extends AbstractChallengeResponseAction implements IPILogger
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PrivacyIDEAAuthenticator.class);
    private PrivacyIDEA privacyIDEA;
    private boolean debug = false;

    /**
     * Constructor
     */
    public PrivacyIDEAAuthenticator() {}

    @Override
    protected final void doPreExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext, @Nonnull PIServerConfigContext piServerConfigContext)
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
                                     .serviceAccount(piServerConfigContext.getConfigParams().getServiceName(), piServerConfigContext.getConfigParams().getServicePass())
                                     .serviceRealm(piServerConfigContext.getConfigParams().getServiceRealm())
                                     .logger(this)
                                     .build();
        }

        PIResponse triggerredResponse = null;
        if (piServerConfigContext.getConfigParams().getTriggerChallenge())
        {
            if (debug)
            {
                LOGGER.info("{} Triggering the challenges...", this.getLogPrefix());
            }
            triggerredResponse = privacyIDEA.triggerChallenges(piContext.getUsername());
        }

        if (triggerredResponse != null)
        {
            if (triggerredResponse.error != null)
            {
                LOGGER.error("{} privacyIDEA server error: {}!", this.getLogPrefix(), triggerredResponse.error.message);
                ActionSupport.buildEvent(profileRequestContext, "AuthenticationException");
                return;
            }

            if (!triggerredResponse.multichallenge.isEmpty())
            {
                if (debug)
                {
                    LOGGER.info("{} Extracting the form data from triggered challenges...", this.getLogPrefix());
                }
                extractChallengeData(piContext, triggerredResponse);
            }
        }
        else
        {
            LOGGER.error("{} triggerChallenge failed. Response was null. Fallback to standard procedure.", this.getLogPrefix());
        }
    }

    @Override
    protected final void doExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext, @Nonnull PIServerConfigContext piServerConfigContext)
    {
        HttpServletRequest request = this.getHttpServletRequest();
        if (request == null)
        {
            LOGGER.error("{} Profile action does not contain an HttpServletRequest.", this.getLogPrefix());
            ActionSupport.buildEvent(profileRequestContext, "AuthenticationException");
        }
        else
        {
            if (request.getParameterValues("pi_otp_input") != null)
            {
                String otp = request.getParameterValues("pi_otp_input")[0];

                PIResponse piResponse;

                if (otp != null)
                {
                    piResponse = privacyIDEA.validateCheck(piContext.getUsername(), otp, piContext.getTransactionID(), null);
                }
                else
                {
                    if (debug)
                    {
                        LOGGER.info("{} Cannot send password because it is null!", this.getLogPrefix());
                    }
                    return;
                }

                if (piResponse != null)
                {
                    piContext.setMessage(piResponse.message);
                    extractChallengeData(piContext, piResponse);

                    if (piResponse.error != null)
                    {
                        LOGGER.error("{} privacyIDEA server error: {}!", this.getLogPrefix(), piResponse.error.message);
                        ActionSupport.buildEvent(profileRequestContext, "AuthenticationException");
                        return;
                    }

                    if (!piResponse.multichallenge.isEmpty())
                    {
                        if (debug)
                        {
                            LOGGER.info("{} Another challenge encountered. Building form...", this.getLogPrefix());
                        }
                        ActionSupport.buildEvent(profileRequestContext, "reload");
                    }
                    else if (piResponse.value)
                    {
                        if (debug)
                        {
                            LOGGER.info("{} Authentication succeeded!", this.getLogPrefix());
                        }
                        ActionSupport.buildEvent(profileRequestContext, "success");
                    }
                    else
                    {
                        if (debug)
                        {
                            LOGGER.info("{} Received a server message. Building form...", this.getLogPrefix());
                        }
                        ActionSupport.buildEvent(profileRequestContext, "reload");
                    }
                }
            }
        }
    }

    /**
     * Extract challenge data from server response, and save it in context.
     *
     * @param piContext privacyIDEA context
     * @param piResponse server response
     */
    private void extractChallengeData(@Nonnull PIContext piContext, @Nonnull PIResponse piResponse)
    {
        if (piResponse.transactionID != null && !piResponse.transactionID.isEmpty())
        {
            piContext.setTransactionID(piResponse.transactionID);
        }
    }

    // Log helper functions
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