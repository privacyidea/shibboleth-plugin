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
        if (piServerConfigContext.getConfigParams().getAuthenticationFlow().equals("triggerChallenge"))
        {
            if (debug)
            {
                LOGGER.info("{} Authentication flow - triggerChallenge.", this.getLogPrefix());
            }

            HttpServletRequest request = Objects.requireNonNull(this.getHttpServletRequestSupplier()).get();
            Map<String, String> headers = this.getHeadersToForward(request);
            PIResponse piResponse = privacyIDEA.triggerChallenges(piContext.getUsername(), headers);

            if (piResponse != null)
            {
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
                        LOGGER.info("{} Extracting the form data from triggered challenges...", this.getLogPrefix());
                    }
                    extractChallengeData(piResponse);
                }
            }
            else
            {
                LOGGER.error("{} triggerChallenge failed. Response was null. Fallback to standard procedure.", this.getLogPrefix());
            }
        }
        else if (piServerConfigContext.getConfigParams().getAuthenticationFlow().equals("sendStaticPass"))
        {
            if (debug)
            {
                LOGGER.info("{} Authentication flow - sendStaticPass.", this.getLogPrefix());
            }

            if (piServerConfigContext.getConfigParams().getStaticPass() == null)
            {
                LOGGER.error("{} Static pass isn't set. Fallback to default authentication flow...", this.getLogPrefix());
            }
            else
            {
                // Call /validate/check with a static pass from the configuration
                // This could already end the authentication if the "passOnNoToken" policy is set.
                // Otherwise, it might trigger the challenges.
                HttpServletRequest request = Objects.requireNonNull(this.getHttpServletRequestSupplier()).get();
                Map<String, String> headers = this.getHeadersToForward(request);
                PIResponse piResponse = privacyIDEA.validateCheck(piContext.getUsername(), piServerConfigContext.getConfigParams().getStaticPass(), headers);

                if (piResponse != null)
                {
                    if (piResponse.error != null)
                    {
                        LOGGER.error("{} privacyIDEA server error: {}!", this.getLogPrefix(), piResponse.error.message);
                        ActionSupport.buildEvent(profileRequestContext, "AuthenticationException");
                        return;
                    }

                    if (piResponse.value)
                    {
                        if (debug)
                        {
                            LOGGER.info("{} Authentication succeeded!", this.getLogPrefix());
                        }
                        ActionSupport.buildEvent(profileRequestContext, "success");
                    }

                    if (!piResponse.multichallenge.isEmpty())
                    {
                        extractChallengeData(piResponse);
                    }
                }
            }
        }
        else
        {
            if (debug)
            {
                LOGGER.info("{} Authentication flow: default.", this.getLogPrefix());
            }
        }
    }
}