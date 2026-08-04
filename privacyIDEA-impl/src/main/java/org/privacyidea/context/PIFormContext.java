/*
 * Copyright 2024 NetKnights GmbH - lukas.matusiewicz@netknights.it
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.privacyidea.context;

import java.util.Collections;
import java.util.List;
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
    private boolean pollInBrowser;
    @Nullable
    private String pollInBrowserUrl;
    private boolean disablePasskey;
    private boolean rememberMeEnabled;
    @Nonnull
    private String imageOtp = "";
    @Nonnull
    private String imagePush = "";
    @Nonnull
    private String imageWebauthn = "";
    @Nonnull
    private String enrollmentLink = "";
    private boolean enrollViaMultichallenge = false;
    private boolean enrollViaMultichallengeOptional = false;
    /** The user's tokens for the tokenSelection flow (usable first, then unusable); empty otherwise. */
    @Nonnull
    private List<TokenListEntry> tokens = Collections.emptyList();

    public PIFormContext(@Nullable String defaultMessage, @Nullable String otpFieldHint, @Nullable Integer otpLength, @Nullable String pollingInterval, boolean pollInBrowser, @Nullable String pollInBrowserUrl, boolean disablePasskey, boolean rememberMeEnabled)
    {
        this.defaultMessage = Objects.requireNonNullElse(defaultMessage, "Please enter your One-Time-Password:");
        this.otpFieldHint = Objects.requireNonNullElse(otpFieldHint, "One-Time-Password (OTP)");
        this.pollingInterval = Objects.requireNonNullElse(pollingInterval, "2");
        this.pollInBrowser = pollInBrowser;
        this.pollInBrowserUrl = pollInBrowserUrl;
        this.disablePasskey = disablePasskey;
        this.rememberMeEnabled = rememberMeEnabled;
        this.otpLength = otpLength;
    }

    public void setMessage(@Nonnull String message) {this.message = message;}

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

    public boolean getPollInBrowser() {return pollInBrowser;}

    @Nullable
    public String getPollInBrowserUrl() {return pollInBrowserUrl;}

    public void setImageOtp(@Nonnull String imageOtp) {this.imageOtp = imageOtp; this.enrollViaMultichallenge = true;}

    @Nonnull
    public String getImageOtp() {return imageOtp;}

    public void setImagePush(@Nonnull String imagePush) {this.imagePush = imagePush; this.enrollViaMultichallenge = true;}

    @Nonnull
    public String getImagePush() {return imagePush;}

    public void setImageWebauthn(@Nonnull String imageWebauthn) {this.imageWebauthn = imageWebauthn;}

    @Nonnull
    public String getImageWebauthn() {return imageWebauthn;}

    public void setEnrollmentLink(@Nonnull String enrollmentLink) {this.enrollmentLink = enrollmentLink;}

    @Nonnull
    public String getEnrollmentLink() {return enrollmentLink;}

    public boolean isEnrollViaMultichallenge() {return enrollViaMultichallenge;}

    public void setEnrollViaMultichallengeOptional(boolean enrollViaMultichallengeOptional) {this.enrollViaMultichallengeOptional = enrollViaMultichallengeOptional;}

    public void setTokens(@Nonnull List<TokenListEntry> tokens) {this.tokens = tokens;}

    @Nonnull
    public List<TokenListEntry> getTokens() {return tokens;}

    public boolean isEnrollViaMultichallengeOptional() {return enrollViaMultichallengeOptional;}

    public boolean isDisablePasskey() {return disablePasskey;}

    public boolean isRememberMeEnabled() {return rememberMeEnabled;}
}
