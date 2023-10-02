package org.privacyidea.context;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opensaml.messaging.context.BaseContext;

public class PIFormContext extends BaseContext
{
    @Nonnull
    private String defaultMessage;
    private String message = "";
    @Nonnull
    private String otpFieldHint;
    @Nullable
    private final Integer otpLength;

    public PIFormContext(@Nullable String defaultMessage, @Nullable String otpFieldHint, @Nullable Integer otpLength)
    {
        this.defaultMessage = Objects.requireNonNullElse(defaultMessage, "Please enter the OTP!");
        this.otpFieldHint = Objects.requireNonNullElse(otpFieldHint, "OTP");
        this.otpLength = otpLength;
    }

    public void setMessage(String message) {this.message = message;}

    @Nonnull
    public String getMessage() {return (!message.isEmpty()) ? message : defaultMessage;}

    @Nonnull
    public String getOtpFieldHint() {return otpFieldHint;}

    @Nullable
    public Integer getOtpLength() {return otpLength;}
}
