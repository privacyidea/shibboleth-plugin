package org.privacyidea.context;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opensaml.messaging.context.BaseContext;

public class PIFormContext extends BaseContext
{
    @Nonnull
    private String defaultMessage;
    @Nonnull
    private String message = "";
    @Nonnull
    private String otpFieldHint;
    @Nullable
    private String pushMessage;
    @Nullable
    private final Integer otpLength;
    @Nonnull
    private String pollingInterval;
    @Nullable
    private String pollInBrowserUrl;
    @Nonnull
    private String imageOtp = "";
    @Nonnull
    private String imagePush = "";
    @Nonnull
    private String imageWebauthn = "";

    public PIFormContext(@Nullable String defaultMessage, @Nullable String otpFieldHint, @Nullable Integer otpLength, @Nullable String pollingInterval, @Nullable String pollInBrowserUrl)
    {
        this.defaultMessage = Objects.requireNonNullElse(defaultMessage, "Please enter the OTP!");
        this.otpFieldHint = Objects.requireNonNullElse(otpFieldHint, "OTP");
        this.pollingInterval = Objects.requireNonNullElse(pollingInterval, "2");
        this.pollInBrowserUrl = pollInBrowserUrl;
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

    @Nonnull
    public String getPollingInterval() {return pollingInterval;}

    @Nullable
    public String getPollInBrowserUrl() {return pollInBrowserUrl;}

    public void setImageOtp(@Nonnull String imageOtp) {this.imageOtp = imageOtp;}

    @Nonnull
    public String getImageOtp() {return imageOtp;}

    public void setImagePush(@Nonnull String imagePush) {this.imagePush = imagePush;}

    @Nonnull
    public String getImagePush() {return imagePush;}

    public void setImageWebauthn(@Nonnull String imageWebauthn) {this.imageWebauthn = imageWebauthn;}

    @Nonnull
    public String getImageWebauthn() {return imageWebauthn;}
}
