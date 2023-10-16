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
    private boolean isPushAvailable = false;
    @Nonnull
    private String mode = "otp";
    @Nullable
    private final String pluginVersion;

    public PIContext(@Nonnull User user, @Nullable String pluginVersion)
    {
        this.user = Constraint.isNotNull(user, "User cannot be null.");
        this.pluginVersion = pluginVersion;
    }

    // Getters and Setters
    @Nonnull
    public String getUsername() {return user.getUsername();}

    public void setTransactionID(@Nonnull String transactionID) {this.transactionID = transactionID;}

    @Nullable
    public String getTransactionID() {return transactionID;}

    public void setIsPushAvailable(boolean pushAvailable) {isPushAvailable = pushAvailable;}

    public boolean getIsPushAvailable() {return isPushAvailable;}

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

    @Nullable
    public String getPluginVersion() {return pluginVersion;}

    public void setFormErrorMessage(@Nullable String formErrorMessage) {this.formErrorMessage = formErrorMessage;}
}
