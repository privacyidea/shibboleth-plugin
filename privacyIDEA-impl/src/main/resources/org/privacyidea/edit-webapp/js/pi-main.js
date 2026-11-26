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

// Convert a byte array to a base64 string
// Used for passkey registration
function base64URLToBytes (base64URLString)
{
    const base64 = base64URLString.replace(/-/g, '+').replace(/_/g, '/');
    const padLength = (4 - (base64.length % 4)) % 4;
    const padded = base64.padEnd(base64.length + padLength, '=');
    const binary = atob(padded);
    const buffer = new ArrayBuffer(binary.length);
    const bytes = new Uint8Array(buffer);
    for (let i = 0; i < binary.length; i++)
    {
        bytes[i] = binary.charCodeAt(i);
    }
    return buffer;
}

// Convert a byte array to a base64 string
// Used for passkey authentication
function bytesToBase64 (bytes)
{
    const binString = Array.from(bytes, (byte) => String.fromCodePoint(byte),).join("");
    return btoa(binString);
}

// Read the passkey challenge from the hidden field "passkeyChallenge".
// Returns the transaction ID and the publicKey options for navigator.credentials.get(), or null without a challenge.
function piPasskeyRequest ()
{
    let passkeyChallenge = piGetValue("passkeyChallenge");
    if (!passkeyChallenge)
    {
        return null;
    }
    let challengeObject = JSON.parse(passkeyChallenge.replace(/(&quot;)/g, "\""));
    let userVerification = "preferred";
    // /validate/initialize uses snake_case (user_verification); PIN-triggered passkey via
    // /validate/check uses camelCase (userVerification). Accept either.
    const uv = challengeObject.user_verification || challengeObject.userVerification;
    if ([ "required", "preferred", "discouraged" ].includes(uv))
    {
        userVerification = uv;
    }
    // No allowCredentials: the request is usernameless. The conditional and the immediate requests also require this.
    return {
        transactionId: challengeObject.transaction_id,
        publicKey: {
            challenge: Uint8Array.from(challengeObject.challenge, c => c.charCodeAt(0)),
            rpId: challengeObject.rpId, userVerification: userVerification,
        },
    };
}

// Put the passkey assertion into the form and submit it with the event "passkey".
function piSubmitPasskeyCredential (credential, transactionId)
{
    let params = {
        transaction_id: transactionId, credential_id: credential.id,
        authenticatorData: bytesToBase64(new Uint8Array(credential.response.authenticatorData)),
        clientDataJSON: bytesToBase64(new Uint8Array(credential.response.clientDataJSON)),
        signature: bytesToBase64(new Uint8Array(credential.response.signature)),
    };
    // userHandle is nullable per the WebAuthn spec — only include it when the authenticator returned one.
    if (credential.response.userHandle)
    {
        params.userHandle = bytesToBase64(new Uint8Array(credential.response.userHandle));
    }
    piSetValue("passkeySignResponse", JSON.stringify(params));
    piSubmitPasskey();
}

// Passkey authentication with the browser dialog. onError (optional) runs after a cancel or an error.
function passkeyAuthentication (onError)
{
    if (piGetValue("mode") === "push")
    {
        piChangeMode("passkey");
    }
    const request = piPasskeyRequest();
    if (!request)
    {
        console.log("Passkey Authentication: Challenge data is empty!");
    }
    else
    {
        piSetValue("passkeyLoginCancelled", "0");
        navigator.credentials.get({publicKey: request.publicKey}).then(credential => {
            piSubmitPasskeyCredential(credential, request.transactionId);
        }, function (error) {
            console.log("Error during passkey authentication: " + error);
            piSetValue("passkeyLoginCancelled", "1");
            if (typeof onError === "function")
            {
                onError(error);
            }
        });
    }
}

// passkey_ui conditional: stops the pending autofill request before the browser dialog opens.
// The browser allows only one WebAuthn request at a time.
let piPasskeyAutofillAbort = null;

function piShowPasskeyButton ()
{
    const wrapper = document.getElementById("passkey");
    if (wrapper)
    {
        wrapper.style.display = "";
    }
}

// passkey_ui conditional: the autofill of the username field lists the passkeys. The request waits until the user
// picks one, or until the page stops it.
function piStartPasskeyAutofill ()
{
    const request = piPasskeyRequest();
    if (!request)
    {
        return;
    }
    piPasskeyAutofillAbort = new AbortController();
    navigator.credentials.get({mediation: "conditional", publicKey: request.publicKey, signal: piPasskeyAutofillAbort.signal})
             .then(credential => piSubmitPasskeyCredential(credential, request.transactionId),
                   error => console.log("Passkey autofill ended: " + error));
}

// passkey_ui immediate: the click on the submit button first asks the browser for a passkey on this device.
// Without one, the browser rejects the request at once with NotAllowedError, and the form submits as usual.
// The request needs the click (user activation), so it cannot start when the page loads.
function piUseImmediatePasskey ()
{
    const submitButton = document.getElementById("passwordStepButton") || document.getElementById("pi-form-submit-button");
    if (!submitButton)
    {
        piShowPasskeyButton();
        return;
    }
    let asked = false;
    submitButton.addEventListener("click", function (event) {
        const request = piPasskeyRequest();
        if (asked || !request)
        {
            return;
        }
        event.preventDefault();
        asked = true;
        navigator.credentials.get({publicKey: request.publicKey, uiMode: "immediate"})
                 .then(credential => piSubmitPasskeyCredential(credential, request.transactionId),
                       error => {
                           console.log("No passkey from the immediate request: " + error);
                           // The second click submits the form with the event of this button.
                           submitButton.click();
                       });
    });
}

// passkey_ui conditional or immediate: the server created the challenge for this page view.
async function piInitPasskeyUi (passkeyUi)
{
    if (!window.PublicKeyCredential)
    {
        piShowPasskeyButton();
        return;
    }
    // The page has a challenge, so the passkey button opens the browser dialog without a server request.
    const passkeyButton = document.getElementById("initPasskeyLogin");
    if (passkeyButton)
    {
        passkeyButton.addEventListener("click", function (event) {
            event.preventDefault();
            piSetValue("passkeyLoginRequested", "0");
            if (piPasskeyAutofillAbort)
            {
                piPasskeyAutofillAbort.abort();
                piPasskeyAutofillAbort = null;
            }
            passkeyAuthentication(function () {
                // The challenge is still valid after a cancel. Offer the passkeys in the autofill again.
                piSetValue("passkeyLoginCancelled", "0");
                if (passkeyUi === "conditional")
                {
                    piStartPasskeyAutofill();
                }
            });
        });
    }

    let supported = false;
    try
    {
        if (passkeyUi === "conditional" && typeof PublicKeyCredential.isConditionalMediationAvailable === "function")
        {
            supported = await PublicKeyCredential.isConditionalMediationAvailable();
        }
        else if (passkeyUi === "immediate" && typeof PublicKeyCredential.getClientCapabilities === "function")
        {
            const capabilities = await PublicKeyCredential.getClientCapabilities();
            supported = !!(capabilities && capabilities.immediateGet);
        }
    }
    catch (error)
    {
        console.log("Could not check the passkey support of the browser: " + error);
    }

    if (!supported)
    {
        piShowPasskeyButton();
    }
    else if (passkeyUi === "conditional")
    {
        piStartPasskeyAutofill();
    }
    else
    {
        piUseImmediatePasskey();
    }
}

// Register a passkey
function registerPasskey ()
{
    let data = JSON.parse(piGetValue("passkeyRegistration").replace(/(&quot;)/g, "\""));
    let excludedCredentials = [];
    if (data.excludeCredentials) {
        for (const cred of data.excludeCredentials) {
            excludedCredentials.push({
                                         id: base64URLToBytes(cred.id),
                                         type: cred.type,
                                     });
        }
    }
    return navigator.credentials.create({
                                            publicKey: {
                                                rp: data.rp,
                                                user: {
                                                    id: base64URLToBytes(data.user.id),
                                                    name: data.user.name,
                                                    displayName: data.user.displayName
                                                },
                                                challenge: Uint8Array.from(data.challenge, c => c.charCodeAt(0)),
                                                pubKeyCredParams: data.pubKeyCredParams,
                                                excludeCredentials: excludedCredentials,
                                                authenticatorSelection: data.authenticatorSelection,
                                                timeout: data.timeout,
                                                extensions: {
                                                    credProps: true,
                                                },
                                                attestation: data.attestation
                                            }
                                        }).then(function (publicKeyCred) {
        let params = {
            credential_id: publicKeyCred.id,
            rawId: bytesToBase64(new Uint8Array(publicKeyCred.rawId)),
            authenticatorAttachment: publicKeyCred.authenticatorAttachment,
            attestationObject: bytesToBase64(
                new Uint8Array(publicKeyCred.response.attestationObject)),
            clientDataJSON: bytesToBase64(new Uint8Array(publicKeyCred.response.clientDataJSON)),
        }
        if (publicKeyCred.response.attestationObject) {
            params.attestationObject = bytesToBase64(
                new Uint8Array(publicKeyCred.response.attestationObject));
        }
        const extResults = publicKeyCred.getClientExtensionResults();
        if (extResults.credProps) {
            params.credProps = extResults.credProps;
        }
        piSetValue("passkeyRegistrationResponse", JSON.stringify(params));
        piSubmit();
    }, function (error) {
        console.log("Error while registering passkey:");
        console.log(error);
        return null;
    });
}

function piMain()
{
    // OTHER LOGIN OPTIONS SECTION VISIBILITY
    if (piGetValue("webauthnSignRequest").length < 1 && piGetValue("isPushAvailable") !== "true" && piGetValue("passkeyChallenge").length < 1
        || piGetValue("isEnrollViaMultichallenge") === "true")
    {
        piDisableElement("otherLoginOptions");
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