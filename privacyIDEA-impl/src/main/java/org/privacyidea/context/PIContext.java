package org.privacyidea.context;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.shibboleth.utilities.java.support.logic.Constraint;
import org.opensaml.messaging.context.BaseContext;

public class PIContext extends BaseContext
{
    private final User user;

    /**
     * Constructor
     */
    public PIContext(@Nonnull User user)
    {
        this.user = Constraint.isNotNull(user, "User cannot be null.");
    }

    @Nonnull
    public String getUsername()
    {
        return this.user.getUsername();
    }
}
