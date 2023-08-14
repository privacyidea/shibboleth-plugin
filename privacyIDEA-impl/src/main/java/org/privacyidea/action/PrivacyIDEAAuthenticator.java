package org.privacyidea.action;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
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

    /**
     * Constructor
     */
    public PrivacyIDEAAuthenticator() {}

    @Override
    protected final void doExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext, @Nonnull PIServerConfigContext piServerConfigContext)
    {
        if (privacyIDEA == null)
        {
            privacyIDEA = PrivacyIDEA.newBuilder(piServerConfigContext.getServerURL(), "privacyIDEA-Shibboleth-Plugin") //todo get useragent
                                     .sslVerify(piServerConfigContext.getVerifySSL()).realm(piServerConfigContext.getRealm()).logger(this).build();
        }

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

                PIResponse piResponse = null;

                if (otp != null)
                {
                    piResponse = privacyIDEA.validateCheck(piContext.getUsername(), otp, null, null);
                }
                else
                {
                    LOGGER.info("{} Cannot send password because it is null!", this.getLogPrefix());
                }

                if (piResponse != null)
                {
                    piContext.setMessage(piResponse.message);

                    if (piResponse.error != null)
                    {
                        LOGGER.error("{} privacyIDEA server error: {}!", this.getLogPrefix(), piResponse.error.message);
                        ActionSupport.buildEvent(profileRequestContext, "AuthenticationException");
                        return;
                    }

                    if (!piResponse.multichallenge.isEmpty())
                    {
                        LOGGER.info("{} Next challenge encountered. Building form...", this.getLogPrefix());
                        ActionSupport.buildEvent(profileRequestContext, "reload");
                    }
                    else if (piResponse.value)
                    {
                        LOGGER.info("{} Authentication succeeded!", this.getLogPrefix());
                        ActionSupport.buildEvent(profileRequestContext, "success");
                    }
                    else
                    {
                        LOGGER.info("{} Received a server message. Building form...", this.getLogPrefix());
                        ActionSupport.buildEvent(profileRequestContext, "reload");
                    }
                }
            }
        }
    }

    @Override
    public void log(String message) {LOGGER.warn("PrivacyIDEA Client: " + message);} //todo change to info

    @Override
    public void error(String message) {LOGGER.error("PrivacyIDEA Client: " + message);}

    @Override
    public void log(Throwable throwable) {LOGGER.warn("PrivacyIDEA Client: " + throwable);} //todo change to info

    @Override
    public void error(Throwable throwable) {LOGGER.error("PrivacyIDEA Client: " + throwable);}
}