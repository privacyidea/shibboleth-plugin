package org.privacyidea.context;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.shibboleth.utilities.java.support.logic.Constraint;
import org.opensaml.messaging.context.BaseContext;

public class PIContext extends BaseContext
{
    @Nonnull
    private final User user;
    @Nonnull
    private final String defaultMessage;
    private String message = "";
    @Nullable
    private String transactionID = null;

    /**
     * Constructor
     */
    public PIContext(@Nonnull User user, @Nullable String defaultMessage)
    {
        this.user = Constraint.isNotNull(user, "User cannot be null.");
        this.defaultMessage = Objects.requireNonNullElse(defaultMessage, "Please enter the OTP!");
    }

    @Nonnull
    public String getUsername() {return user.getUsername();}

    public void setMessage(String message) {this.message = message;}

    public String getMessage()
    {
        if (!message.isEmpty())
        {
            return message;
        }
        else
        {
            return defaultMessage;
        }
    }

    public void setTransactionID(@Nonnull String transactionID) {this.transactionID = transactionID;}

    @Nullable
    public String getTransactionID() {return transactionID;}
}
