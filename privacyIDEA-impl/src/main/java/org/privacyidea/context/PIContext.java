package org.privacyidea.context;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.shibboleth.utilities.java.support.logic.Constraint;
import org.opensaml.messaging.context.BaseContext;

public class PIContext extends BaseContext
{
    @Nonnull
    private final User user;
    @Nullable
    private String transactionID = null;
    @Nullable
    private String webauthnSignResponse = null;
    @Nullable
    private String origin = null;
    @Nonnull
    private String mode = "otp";

    public PIContext(@Nonnull User user)
    {
        this.user = Constraint.isNotNull(user, "User cannot be null.");
    }

    // Getters and Setters
    @Nonnull
    public String getUsername() {return user.getUsername();}

    public void setTransactionID(@Nonnull String transactionID) {this.transactionID = transactionID;}

    @Nullable
    public String getTransactionID() {return transactionID;}

    @Nullable
    public String getWebauthnSignResponse()
    {
        return webauthnSignResponse;
    }

    public void setWebauthnSignResponse(@Nullable String webauthnSignResponse)
    {
        this.webauthnSignResponse = webauthnSignResponse;
    }

    @Nullable
    public String getOrigin()
    {
        return origin;
    }

    public void setOrigin(@Nullable String origin)
    {
        this.origin = origin;
    }

    @Nonnull
    public String getMode()
    {
        return mode;
    }

    public void setMode(@Nonnull String mode)
    {
        this.mode = mode;
    }
}
