package org.privacyidea.action;

import net.shibboleth.idp.authn.AbstractValidationAction;
import net.shibboleth.idp.authn.context.AuthenticationContext;
import net.shibboleth.idp.authn.context.UsernameContext;
import net.shibboleth.idp.authn.principal.UsernamePrincipal;
import org.jetbrains.annotations.NotNull;
import org.opensaml.profile.action.ActionSupport;
import org.opensaml.profile.context.ProfileRequestContext;
import org.privacyidea.context.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.security.auth.Subject;

public class ValidatePasskeyResponse extends AbstractValidationAction
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidatePasskeyResponse.class);
    private String username;

    public ValidatePasskeyResponse() {}

    protected void doExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull AuthenticationContext authenticationContext)
    {
        UsernameContext userCtx = profileRequestContext.getSubcontext(UsernameContext.class, false);
        if (userCtx != null && StringUtil.isNotBlank(userCtx.getUsername()))
        {
            username = userCtx.getUsername();

            //this.recordSuccess(profileRequestContext);
            this.buildAuthenticationResult(profileRequestContext, authenticationContext);
            ActionSupport.buildEvent(profileRequestContext, "success");
        }
        else
        {
            LOGGER.error("{} Unknown passkey user. Restarting the authentication process.", this.getLogPrefix());
            ActionSupport.buildEvent(profileRequestContext, "unknownUser");
        }
    }

    @Override
    protected @NotNull Subject populateSubject(@NotNull Subject subject)
    {
        UsernamePrincipal usernamePrincipal = new UsernamePrincipal(username);
        subject.getPrincipals().add(usernamePrincipal);
        return subject;
    }
}