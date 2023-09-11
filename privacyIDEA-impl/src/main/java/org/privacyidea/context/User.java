package org.privacyidea.context;

import javax.annotation.Nonnull;
import net.shibboleth.utilities.java.support.annotation.constraint.NotEmpty;
import org.jetbrains.annotations.NotNull;

public class User
{
    @Nonnull
    @NotEmpty
    private final String username;
    
    public User(@Nonnull @NotEmpty String username)
    {
        this.username = username;
    }

    @NotNull
    @NotEmpty
    public String getUsername()
    {
        return username;
    }
}