package org.privacyidea.action;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import org.opensaml.profile.action.ActionSupport;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.PIResponse;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIServerConfigContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TriggerChallenge extends AbstractChallengeResponseAction
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PrivacyIDEAAuthenticator.class);

    public TriggerChallenge() {}

    @Override
    protected final void doExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext, @Nonnull PIServerConfigContext piServerConfigContext)
    {
        if (piServerConfigContext.getConfigParams().getTriggerChallenge())
        {
            if (debug)
            {
                LOGGER.info("{} Triggering challenges...", this.getLogPrefix());
            }

            HttpServletRequest request = this.getHttpServletRequest();
            Map<String, String> headers = new LinkedHashMap<>();
            if (request != null)
            {
                 headers = this.getHeadersToForward(request);
            }
            else
            {
                LOGGER.info("{} Failed to attach headers to triggerchallenge request because HTTP Servlet Request was null", this.getLogPrefix());
            }

            PIResponse triggerredResponse = privacyIDEA.triggerChallenges(piContext.getUsername(), headers);

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
                    extractChallengeData(triggerredResponse);
                }
            }
            else
            {
                LOGGER.error("{} triggerChallenge failed. Response was null. Fallback to standard procedure.", this.getLogPrefix());
            }
        }
        else
        {
            if (debug)
            {
                LOGGER.info("{} triggerchallenge not enabled.", this.getLogPrefix());
            }
        }
    }
}
