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
        console.log(id + " is null!");
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
        let form = document.getElementById("privacyidea-form");
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
    else
    {
        console.log(id + " is null!");
    }
}

window.piElementCheck = function elementCheck(field)
{
    return typeof (field) != 'undefined' && field != null;
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
        console.log(id + " is null!");
    }
}

window.piChangeMode = function changeMode(newMode)
{
    piSetValue("mode", newMode);
    document.getElementById("pi-form-submit-button").click();
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

window.piSubmit = function clickSubmitButton()
{
    let proceedField = piCreateField("_eventId_proceed", "proceed");
    let form = document.getElementById("privacyidea-form");
    form.appendChild(proceedField);
    form.submit();
}