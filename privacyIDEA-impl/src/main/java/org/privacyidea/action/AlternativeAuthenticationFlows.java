package org.privacyidea.action;

import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import org.opensaml.profile.action.ActionSupport;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.PIResponse;
import org.privacyidea.context.PIContext;
import org.privacyidea.context.PIServerConfigContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlternativeAuthenticationFlows extends AbstractChallengeResponseAction
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AlternativeAuthenticationFlows.class);

    public AlternativeAuthenticationFlows() {}

    @Override
    protected final void doExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext, @Nonnull PIServerConfigContext piServerConfigContext)
    {
        //todo check the authentication flow and perform the choosed one

        if (piServerConfigContext.getConfigParams().getTriggerChallenge())
        {
            if (debug)
            {
                LOGGER.info("{} Triggering challenges...", this.getLogPrefix());
            }

            HttpServletRequest request = Objects.requireNonNull(this.getHttpServletRequestSupplier()).get();
            Map<String, String> headers = this.getHeadersToForward(request);
            PIResponse triggeredResponse = privacyIDEA.triggerChallenges(piContext.getUsername(), headers);

            if (triggeredResponse != null)
            {
                if (triggeredResponse.error != null)
                {
                    LOGGER.error("{} privacyIDEA server error: {}!", this.getLogPrefix(), triggeredResponse.error.message);
                    ActionSupport.buildEvent(profileRequestContext, "AuthenticationException");
                    return;
                }

                if (!triggeredResponse.multichallenge.isEmpty())
                {
                    if (debug)
                    {
                        LOGGER.info("{} Extracting the form data from triggered challenges...", this.getLogPrefix());
                    }
                    extractChallengeData(triggeredResponse);
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
