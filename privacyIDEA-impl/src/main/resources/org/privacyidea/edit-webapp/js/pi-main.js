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

function doWebAuthn()
{
    // If we are in push mode, reload the page because in push mode the page refreshes every x seconds which could interrupt WebAuthn
    // Afterward, WebAuthn is started directly
    if (piGetValue("mode") === "push")
    {
        piChangeMode("webauthn");
    }
    else
    {
        try
        {
            const requestStr = piGetValue("webauthnSignRequest");
            const requestJSON = JSON.parse(requestStr);
            const webAuthnSignResponse = window.pi_webauthn.sign(requestJSON);

            webAuthnSignResponse.then((webauthnresponse) =>
            {
                piSetValue("webauthnSignResponse", JSON.stringify(webauthnresponse));
                piSubmit();
            });
        }
        catch (err)
        {
            console.log("Error while trying WebAuthn: " + err);
            piSetValue("errorMessage", "Error while trying WebAuthn: " + err);
        }
    }
}

function piMain()
{
    // ALTERNATE TOKEN SECTION VISIBILITY
    if (piGetValue("webauthnSignRequest").length < 1 && piGetValue("isPushAvailable") !== true)
    {
        piDisableElement("alternateToken");
    }
    // PUSH
    if (piGetValue("mode") === "push")
    {
        piDisableElement("pi-form-submit-button");
        piDisableElement("otp");
        window.onload = () =>
        {
            window.setTimeout(() =>
            {
                piSubmit();
            }, parseInt(piGetValue("pollingInterval")) * 1000);
        }
    }
    // WEBAUTHN
    if (piGetValue("mode") === "webauthn")
    {
        window.onload = () =>
        {
            doWebAuthn();
        }
    }
    if (!window.location.origin)
    {
        window.location.origin = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port : '');
    }
    piSetValue("origin", window.origin);
}

// Wait until the document is ready
document.addEventListener("DOMContentLoaded", function ()
{
    piMain();
});