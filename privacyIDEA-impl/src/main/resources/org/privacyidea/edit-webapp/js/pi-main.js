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

// Passkey authentication
function passkeyAuthentication ()
{
    if (piGetValue("mode") === "push")
    {
        piChangeMode("passkey");
    }
    let passkeyChallenge = piGetValue("passkeyChallenge");
    if (!passkeyChallenge)
    {
        console.log("Passkey Authentication: Challenge data is empty!");
    }
    else
    {
        piSetValue("passkeyLoginCancelled", "0");
        let challengeObject = JSON.parse(passkeyChallenge.replace(/(&quot;)/g, "\""));
        let userVerification = "preferred";
        if ([ "required", "preferred", "discouraged" ].includes(challengeObject.user_verification))
        {
            userVerification = challengeObject.user_verification;
        }
        navigator.credentials.get({
                                      publicKey: {
                                          challenge: Uint8Array.from(challengeObject.challenge, c => c.charCodeAt(0)),
                                          rpId: challengeObject.rpId, userVerification: userVerification,
                                      },
                                  }).then(credential => {
            let params = {
                transaction_id: challengeObject.transaction_id, credential_id: credential.id,
                authenticatorData: bytesToBase64(new Uint8Array(credential.response.authenticatorData)),
                clientDataJSON: bytesToBase64(new Uint8Array(credential.response.clientDataJSON)),
                signature: bytesToBase64(new Uint8Array(credential.response.signature)),
                userHandle: bytesToBase64(new Uint8Array(credential.response.userHandle)),
            };
            piSetValue("passkeySignResponse", JSON.stringify(params));
            piSubmit();
        }, function (error) {
            console.log("Error during passkey authentication: " + error);
            piSetValue("passkeyLoginCancelled", "1");
        });
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