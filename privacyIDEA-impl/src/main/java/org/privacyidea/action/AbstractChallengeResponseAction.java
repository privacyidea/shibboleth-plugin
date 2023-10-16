package org.privacyidea.action;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import net.shibboleth.idp.authn.context.AuthenticationContext;
import net.shibboleth.idp.profile.AbstractProfileAction;
import net.shibboleth.idp.Version;
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
import org.springframework.core.io.support.PropertiesLoaderSupport;
import org.springframework.core.io.support.PropertiesLoaderUtils;

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
                            String shibbVersion = Version.getVersion();
                            String pluginVersion = piContext.getPluginVersion();
                            String userAgent = "privacyIDEA-Shibboleth/" + pluginVersion + ", Shibboleth IdP/" + shibbVersion;
                            privacyIDEA = PrivacyIDEA.newBuilder(piServerConfigContext.getConfigParams().getServerURL(), userAgent)
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
        if (piResponse.triggeredTokenTypes().contains("webauthn"))
        {
            piContext.setWebauthnSignRequest(piResponse.mergedSignRequest());
        }
    }

    /**
     * Search for the configured headers in HttpServletRequest and return all found with their values.
     *
     * @param request http servlet request
     * @return headers to forward with their values
     */
    protected Map<String, String> getHeadersToForward(HttpServletRequest request)
    {
        Map<String, String> headersToForward = new LinkedHashMap<>();
        if (piServerConfigContext.getConfigParams().getForwardHeaders() != null && !piServerConfigContext.getConfigParams().getForwardHeaders().isEmpty())
        {
            String cleanHeaders = piServerConfigContext.getConfigParams().getForwardHeaders().replaceAll(" ", "");
            List<String> headersList = List.of(cleanHeaders.split(","));

            for (String headerName : headersList.stream().distinct().collect(Collectors.toList()))
            {
                List<String> headerValues = new ArrayList<>();
                Enumeration<String> e = request.getHeaders(headerName);
                if (e != null)
                {
                    while (e.hasMoreElements())
                    {
                        headerValues.add(e.nextElement());
                    }
                }

                if (!headerValues.isEmpty())
                {
                    String temp = String.join(",", headerValues);
                    headersToForward.put(headerName, temp);
                }
                else
                {
                    LOGGER.info("{} No values for header \"" + headerName + "\" found.", this.getLogPrefix());
                }
            }
        }
        return headersToForward;
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