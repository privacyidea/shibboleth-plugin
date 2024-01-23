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

window.onload = () =>
{
    piDisableElement("pushButton");
    let worker;
    if (typeof (Worker) !== "undefined")
    {
        if (typeof (worker) == "undefined")
        {
            worker = new Worker('../../../js/pi-pollTransaction.worker.js');
            document.getElementById("pi-form-submit-button").addEventListener('click', function (e)
            {
                worker.terminate();
                worker = undefined;
            });
            worker.postMessage({'cmd': 'url', 'msg': piGetValue("pollInBrowserUrl")});
            worker.postMessage({'cmd': 'transactionID', 'msg': piGetValue("transactionID")});
            worker.postMessage({'cmd': 'start'});
            worker.addEventListener('message', function (e)
            {
                let data = e.data;
                switch (data.status)
                {
                    case 'success':
                        piSubmit();
                        break;
                    case 'error':
                        let errorMessage = "Poll in browser error: " + data.message;
                        console.log(errorMessage);
                        piSetValue("errorMessage", errorMessage);
                        piSetValue("pollInBrowserFailed", true);
                        piEnableElement("pushButton");
                        worker = undefined;
                }
            });
        }
    }
    else
    {
        console.log("Sorry! No Web Worker support.");
        worker.terminate();
        piSetValue("errorMessage", "Poll in browser error: No Web Worker support.");
        piSetValue("pollInBrowserFailed", true);
        piEnableElement("pushButton");
    }
}