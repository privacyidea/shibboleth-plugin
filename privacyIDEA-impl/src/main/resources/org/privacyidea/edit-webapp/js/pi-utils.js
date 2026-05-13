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

window.piGetValue = function getValue(id)
{
    const element = document.getElementById(id);
    if (element === null)
    {
        return "";
    }
    else
    {
        return element.value;
    }
}

window.piSetValue = function setValue(id, value)
{
    const element = document.getElementById(id);
    if (element !== null)
    {
        element.value = value;
    }
    else
    {
        let form = document.querySelector("#privacyidea-form") || document.querySelector("#username-password-form");
        let field = piCreateField(id, value);
        form.appendChild(field);
    }
}

window.piDisableElement = function disableElement(id)
{
    const element = document.getElementById(id);
    if (element !== null)
    {
        element.style.display = "none";
    }
}

window.piEnableElement = function enableElement(id)
{
    const element = document.getElementById(id);
    if (element !== null)
    {
        element.style.display = "initial";
    }
    else
    {
        console.log(id + " is null! Cannot enable element.");
    }
}

window.piChangeMode = function changeMode(newMode)
{
    piSetValue("mode", newMode);
    piSubmit();
}

window.piCreateField = function createField(name, value)
{
    let field = document.createElement("input");
    field.type = "hidden";
    field.name = name;
    field.id = name;
    field.value = value;
    return field;
}

function piFindForm()
{
    // main.vm uses #privacyidea-form; usernamePasswordForm.vm uses #username-password-form.
    return document.querySelector("#privacyidea-form") || document.querySelector("#username-password-form");
}

window.piSubmit = function clickSubmitButton()
{
    let proceedField = piCreateField("_eventId_proceed", "proceed");
    let form = piFindForm();
    form.appendChild(proceedField);
    form.submit();
}

// Submit using the _eventId_passkey event so the flow routes to piAuthenticator from either
// view-state (main or username/password) — only the username form's "proceed" goes elsewhere.
window.piSubmitPasskey = function clickSubmitPasskeyButton()
{
    let passkeyField = piCreateField("_eventId_passkey", "passkey");
    let form = piFindForm();
    form.appendChild(passkeyField);
    form.submit();
}