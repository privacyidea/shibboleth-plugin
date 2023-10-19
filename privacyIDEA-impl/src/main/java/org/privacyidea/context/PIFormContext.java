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
    private String pushMessage;
    @Nullable
    private final Integer otpLength;
    private String pollingInterval;
    @Nullable
    private String imageOtp;
    @Nullable
    private String imagePush;
    @Nullable
    private String imageWebauthn;

    public PIFormContext(@Nullable String defaultMessage, @Nullable String otpFieldHint, @Nullable Integer otpLength, @Nullable String pollingInterval)
    {
        this.defaultMessage = Objects.requireNonNullElse(defaultMessage, "Please enter the OTP!");
        this.otpFieldHint = Objects.requireNonNullElse(otpFieldHint, "OTP");
        this.pollingInterval = Objects.requireNonNullElse(pollingInterval, "2");
        this.otpLength = otpLength;
    }

    public void setMessage(String message) {this.message = message;}

    @Nonnull
    public String getMessage() {return (!message.isEmpty()) ? message : defaultMessage;}

    @Nonnull
    public String getOtpFieldHint() {return otpFieldHint;}

    public void setPushMessage(@Nullable String pushMessage) {this.pushMessage = pushMessage;}

    @Nullable
    public String getPushMessage() {return pushMessage;}

    @Nullable
    public Integer getOtpLength() {return otpLength;}

    public String getPollingInterval() {return pollingInterval;}

    public void setImageOtp(@Nullable String imageOtp) {this.imageOtp = imageOtp;}

    @Nullable
    public String getImageOtp() {return imageOtp;}

    public void setImagePush(@Nullable String imagePush) {this.imagePush = imagePush;}

    @Nullable
    public String getImagePush() {return imagePush;}

    public void setImageWebauthn(@Nullable String imageWebauthn) {this.imageWebauthn = imageWebauthn;}

    @Nullable
    public String getImageWebauthn() {return imageWebauthn;}
}
