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
    private String piMessage = "";

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

    public void setMessage(String message) {piMessage = message;}

    public String getMessage()
    {
        if (!piMessage.isEmpty())
        {
            return piMessage;
        }
        else
        {
            return defaultMessage;
        }
    }
}
