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
    public PrivacyIDEAAuthenticator()
    {
    }

    @Override
    protected final void doExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext, @Nonnull
    PIServerConfigContext piServerConfigContext)
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
                String extractedOTP = request.getParameterValues("pi_otp_input")[0];

                PIResponse piResponse = null;

                if (extractedOTP != null)
                {
                    piResponse = privacyIDEA.validateCheck(piContext.getUsername(), extractedOTP, null, null);
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
                        LOGGER.error("{} privacyIDEA server error: " + piResponse.error.message, this.getLogPrefix());
                        ActionSupport.buildEvent(profileRequestContext, "AuthenticationException");
                    }
                    if (!piResponse.multichallenge.isEmpty())
                    {
                        ActionSupport.buildEvent(profileRequestContext, "reload");
                    }
                    else if (piResponse.value)
                    {
                        ActionSupport.buildEvent(profileRequestContext, "success");
                    }
                    else
                    {
                        ActionSupport.buildEvent(profileRequestContext, "AuthenticationException");
                    }
                }
            }
        }
    }

    @Override
    public void log(String message)
    {
        LOGGER.info("PrivacyIDEA Client: " + message);
    }

    @Override
    public void error(String message)
    {
        LOGGER.error("PrivacyIDEA Client: " + message);
    }

    @Override
    public void log(Throwable throwable)
    {
        LOGGER.info("PrivacyIDEA Client: " + throwable);
    }

    @Override
    public void error(Throwable throwable)
    {
        LOGGER.error("PrivacyIDEA Client: " + throwable);
    }
}