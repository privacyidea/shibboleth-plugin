package org.privacyidea.context;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opensaml.messaging.context.BaseContext;

public class PIFormContext extends BaseContext
{
    @Nonnull
    private String mode = "otp";
    private String webauthnSignRequest = "";
    @Nonnull
    private final String defaultMessage;
    private String message = "";
    @Nonnull
    private final String otpFieldHint;

    public PIFormContext(@Nullable String defaultMessage, @Nullable String otpFieldHint)
    {
        this.defaultMessage = Objects.requireNonNullElse(defaultMessage, "Please enter the OTP!");
        this.otpFieldHint = Objects.requireNonNullElse(otpFieldHint, "OTP");
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

    public String getWebauthnSignRequest()
    {
        return webauthnSignRequest;
    }

    public void setWebauthnSignRequest(String webauthnSignRequest)
    {
        this.webauthnSignRequest = webauthnSignRequest;
    }

    public void setMessage(String message) {this.message = message;}

    @Nonnull
    public String getMessage() {return (!message.isEmpty()) ? message : defaultMessage;}

    @Nonnull
    public String getOtpFieldHint() {return otpFieldHint;}
}