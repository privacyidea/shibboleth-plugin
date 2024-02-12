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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class Config
{
    @Nonnull
    private final String serverURL;
    @Nullable
    private final String realm;
    private final boolean verifySSL;
    private final boolean debug;
    @Nonnull
    private final String authenticationFlow;
    @Nullable
    private final String serviceName;
    @Nullable
    private final String servicePass;
    @Nullable
    private final String serviceRealm;
    @Nullable
    private final String staticPass;
    private final boolean pollInBrowser;
    @Nullable
    private final String forwardHeaders;
    @Nullable
    private final String otpLength;

    public Config(@Nonnull String serverURL, @Nullable String realm, boolean verifySSL, @Nonnull String authenticationFlow,
                  @Nullable String serviceName, @Nullable String servicePass, @Nullable String serviceRealm, @Nullable String staticPass,
                  boolean pollInBrowser, @Nullable String forwardHeaders, @Nullable String otpLength, boolean debug)
    {
        this.serverURL = serverURL;
        this.realm = realm;
        this.verifySSL = verifySSL;
        this.authenticationFlow = authenticationFlow;
        this.serviceName = serviceName;
        this.servicePass = servicePass;
        this.serviceRealm = serviceRealm;
        this.staticPass = staticPass;
        this.pollInBrowser = pollInBrowser;
        this.forwardHeaders = forwardHeaders;
        this.otpLength = otpLength;
        this.debug = debug;
    }

    // Config getters
    @Nonnull
    public String getServerURL()
    {
        return serverURL;
    }

    @Nullable
    public String getRealm()
    {
        return realm;
    }

    public boolean getVerifySSL()
    {
        return verifySSL;
    }

    @Nonnull
    public String getAuthenticationFlow()
    {
        return authenticationFlow;
    }

    @Nullable
    public String getServiceName()
    {
        return serviceName;
    }

    @Nullable
    public String getServicePass()
    {
        return servicePass;
    }

    @Nullable
    public String getServiceRealm()
    {
        return serviceRealm;
    }

    public boolean getPollInBrowser()
    {
        return pollInBrowser;
    }

    @Nullable
    public String getStaticPass()
    {
        return staticPass;
    }

    @Nullable
    public String getForwardHeaders()
    {
        return forwardHeaders;
    }

    @Nullable
    public String getOtpLength()
    {
        return otpLength;
    }

    public boolean getDebug()
    {
        return debug;
    }
}