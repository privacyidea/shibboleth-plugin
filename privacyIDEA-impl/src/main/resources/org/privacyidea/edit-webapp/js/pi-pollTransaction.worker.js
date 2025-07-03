/*
 * Copyright 2024 NetKnights GmbH - lukas.matusiewicz@netknights.it
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

let url;
let params;

self.addEventListener('message', function (e) {
    let data = e.data;

    switch (data.cmd) {
        case 'url':
            url = data.msg + "/validate/polltransaction";
            break;
        case 'transactionID':
            params = "transaction_id=" + data.msg;
            break;
        case 'start':
            if (url && url.length > 0 && params && params.length > 0) {
                setInterval(pollTransactionInBrowser, 300);
            }
            break;
    }
});

async function pollTransactionInBrowser() {
    try {
        const response = await fetch(url + "?" + params, { method: "GET" });
        if (response.ok) {
            const data = await response.json();
            if (data['result'] && data['result']['value'] === true) {
                self.postMessage({ 'message': 'Polling in browser: Push message confirmed!', 'status': 'success' });
                self.close();
            }
        } else {
            self.postMessage({ 'message': response.statusText, 'status': 'error' });
            self.close();
        }
    } catch (e) {
        self.postMessage({ 'message': e.toString(), 'status': 'error' });
        self.close();
    }
}