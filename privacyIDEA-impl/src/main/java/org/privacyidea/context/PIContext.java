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
    @Nonnull
    private final String otpFieldHint;

    /**
     * Constructor
     */
    public PIContext(@Nonnull User user, @Nullable String defaultMessage, @Nullable String otpFieldHint)
    {
        this.user = Constraint.isNotNull(user, "User cannot be null.");
        this.defaultMessage = Objects.requireNonNullElse(defaultMessage, "Please enter the OTP!");
        this.otpFieldHint = Objects.requireNonNullElse(otpFieldHint, "OTP");
    }

    // Getters and Setters
    @Nonnull
    public String getUsername() {return user.getUsername();}
    public void setMessage(String message) {this.message = message;}
    @Nonnull
    public String getMessage() {return (!message.isEmpty()) ? message : defaultMessage;}
    public void setTransactionID(@Nonnull String transactionID) {this.transactionID = transactionID;}
    @Nullable
    public String getTransactionID() {return transactionID;}
    @Nonnull
    public String getOtpFieldHint() {return otpFieldHint;}
}
