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

function piEventListeners()
{
    let otp = document.querySelector("#otp")
    if (otp)
    {
        otp.addEventListener("keyup", () => {
            if (piGetValue('otp').length === parseInt(piGetValue("otpLength")))
            {
                piSubmit();
            }
        });
    }

    let otpButton = document.querySelector("#otpButton")
    if (otpButton)
    {
        otpButton.addEventListener("click", () => {
            //piSetValue("silentModeChange", "1");
            piChangeMode("otp");
        });
    }

    let pushButton = document.querySelector("#pushButton")
    if (pushButton)
    {
        pushButton.addEventListener("click", () => {
            piChangeMode("push");
        });
    }
    let webauthnButton = document.querySelector("#webauthnButton")
    if (webauthnButton)
    {
        webauthnButton.addEventListener("click", () => {
            doWebAuthn();
        });
    }
}

// Wait until the document is ready
document.addEventListener("DOMContentLoaded", function () {
    piEventListeners();
});