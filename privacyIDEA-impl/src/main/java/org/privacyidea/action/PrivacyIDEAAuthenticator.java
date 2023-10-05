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

public class PrivacyIDEAAuthenticator extends AbstractChallengeResponseAction
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PrivacyIDEAAuthenticator.class);

    public PrivacyIDEAAuthenticator() {}

    @Override
    protected final void doExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull PIContext piContext, @Nonnull PIServerConfigContext piServerConfigContext)
    {
        HttpServletRequest request = Objects.requireNonNull(this.getHttpServletRequestSupplier()).get();
        Map<String, String> headers = this.getHeadersToForward(request);

        piContext.setMode(request.getParameterValues("mode")[0]);
        piContext.setWebauthnSignResponse(request.getParameterValues("webauthnSignResponse")[0]);
        piContext.setOrigin(request.getParameterValues("origin")[0]);
        piContext.setFormErrorMessage(request.getParameterValues("errorMessage")[0]);

        PIResponse piResponse = null;
        if (piContext.getWebauthnSignResponse() != null && !piContext.getWebauthnSignResponse().isEmpty())
        {
            if (piContext.getOrigin() == null || piContext.getOrigin().isEmpty())
            {
                LOGGER.error("Origin is missing for WebAuthn authentication!");
            }
            else
            {
                piResponse = privacyIDEA.validateCheckWebAuthn(piContext.getUsername(), piContext.getTransactionID(), piContext.getWebauthnSignResponse(),
                                                               piContext.getOrigin(), headers);
            }
        }
        else if (piContext.getMode().equals("otp"))
        {
            if (request.getParameterValues("pi_otp_input") != null)
            {
                String otp = request.getParameterValues("pi_otp_input")[0];
                if (otp != null)
                {
                    piResponse = privacyIDEA.validateCheck(piContext.getUsername(), otp, piContext.getTransactionID(), headers);
                }
                else
                {
                    if (debug)
                    {
                        LOGGER.info("{} Cannot send password because it is null!", this.getLogPrefix());
                    }
                    return;
                }
            }
        }
        if (piResponse != null)
        {
            extractChallengeData(piResponse);
            if (piResponse.error != null)
            {
                LOGGER.error("{} privacyIDEA server error: {}!", this.getLogPrefix(), piResponse.error.message);
                piContext.setFormErrorMessage(piResponse.error.message);
                ActionSupport.buildEvent(profileRequestContext, "reload");
            }
            if (!piResponse.multichallenge.isEmpty())
            {
                if (debug)
                {
                    LOGGER.info("{} Challenge triggered, building form...", this.getLogPrefix());
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
                    LOGGER.info("{} Received response from server, building form...", this.getLogPrefix());
                }
                ActionSupport.buildEvent(profileRequestContext, "reload");
            }
        }
    }
}