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
    @Nonnull
    private String webauthnSignRequest = "";
    @Nullable
    private String webauthnSignResponse = null;
    @Nullable
    private String formErrorMessage = null;
    @Nullable
    private String origin = null;
    @Nonnull
    private String mode = "otp";
    @Nonnull
    private final String otpFieldHint;
    @Nullable
    private final Integer otpLength;

    public PIContext(@Nonnull User user, @Nullable String defaultMessage, @Nullable String otpFieldHint, @Nullable Integer otpLength)
    {
        this.user = Constraint.isNotNull(user, "User cannot be null.");
        this.defaultMessage = Objects.requireNonNullElse(defaultMessage, "Please enter the OTP!");
        this.otpFieldHint = Objects.requireNonNullElse(otpFieldHint, "OTP");
        this.otpLength = otpLength;
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

    @Nullable
    public String getWebauthnSignResponse() {return webauthnSignResponse;}

    public void setWebauthnSignResponse(@Nullable String webauthnSignResponse) {this.webauthnSignResponse = webauthnSignResponse;}

    @Nullable
    public String getOrigin() {return origin;}

    public void setOrigin(@Nullable String origin) {this.origin = origin;}

    @Nonnull
    public String getMode() {return mode;}

    public void setMode(@Nonnull String mode) {this.mode = mode;}

    @Nonnull
    public String getWebauthnSignRequest() {return webauthnSignRequest;}

    public void setWebauthnSignRequest(@Nonnull String webauthnSignRequest) {this.webauthnSignRequest = webauthnSignRequest;}

    @Nullable
    public String getFormErrorMessage() {return formErrorMessage;}

    public void setFormErrorMessage(@Nullable String formErrorMessage) {this.formErrorMessage = formErrorMessage;}
    
    @Nonnull
    public String getOtpFieldHint() {return otpFieldHint;}

    @Nullable
    public Integer getOtpLength() {return otpLength;}
}
