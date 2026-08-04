# shibboleth-plugin

### Shibboleth Version
The main branch is compatible with IdP Version >=5.0.0.

### Installation:
1. **Copy the package to the server:**
    - Choose the archive type for your system from the release page (.zip, .tar.gz, .tar.bz2).
    - Copy it with the corresponding signature data (.asc).
    - Place both files somewhere in your Shibboleth server.
2. **Open the terminal and run: `sudo -i` for the admin rights.**
3. **Enable the MFA Module: `$idp_install_path/bin/module.sh -t idp.authn.MFA || $idp_install_path/bin/module.sh -e idp.authn.MFA`.**
4. **Check if the MFA Module is activated: `$idp_install_path/bin/module.sh -l`.** 
5. **Install the privacyIDEA Plugin: `$idp_install_path/bin/plugin.sh -i path/to/zip/from/step/1 --noCheck`**<br>
Note: The installer will automatically install and enable the privacyIDEA Module. You can check the state by repeating the step 4.
If you need to enable the privacyIDEA module run: `$idp_install_path/bin/module.sh -e idp.authn.privacyIDEA`.** 
7. **Check if the privacyIDEA Plugin is installed correctly: `$idp_install_path/bin/plugin.sh -l`**<br>
Note: If the *org.privacyidea.privacyIDEA* is on the list, the plugin was installed successfully!<br>
Otherwise, re-run the command from step 5 with `--verbose`.

### Configuration:
1. **Update the *privacyidea.properties* file (`$idp_install_path/conf/authn/privacyidea.properties`) by adding your own configuration data.**<br>
Updating the following parameters is required to ensure at least the very basic functionality:
   - `privacyidea.server_url`
   - `privacyidea.verify_ssl`
   - `privacyidea.authentication_flow`

2. **Add the privacyIDEA subflow to the MFA flow.**<br>
   - Path to the MFA flow configuration file: `$idp_install_path/conf/authn/mfa-authn-config.xml`.
   - Example of the configuration: [MFA Config Example](docs/mfaConfigExample).
   - Remember to activate the MFA flow.

3. **Turn on the MFA Module by updating the following file: `$idp_install_path/conf/authn/authn.properties`.**<br>
   - Example of the basic configuration: [Authn Config Example](docs/authnConfigExample).

### Passkey:
**If you want to use passkey authentication without the password module, you can adjust the mfa-authn-config.xml file to use the privacyIDEA subflow directly.**<br>
Note: This will change the authentication flow to allow passkey authentication without need for entering the username and password.<br>
Example of the configuration: [MFA Config Example](docs/mfaConfigExample).

This is also located in the *privacyidea.properties* file (`$idp_install_path/conf/authn/privacyidea.properties`).


### User Verification (Passkey / Standalone):
**Important Note on User Existence:**<br>
When using the plugin in **Passkey** or **Standalone** mode (where the standard `authn/Password` flow is skipped), the plugin asserts the identity (username) returned by the privacyIDEA server.
The plugin **does not** verify if this user exists in the local IdP user store (e.g., LDAP, SQL, or htpasswd).
However, if you enter username and password in these modes, it will always send these parameters to privacyIDEA for validation.

To ensure that only valid local users can log in, you should rely on the standard Shibboleth mechanisms that run after authentication:
1.  **Attribute Resolution**: Ensure your Attribute Resolver is configured to look up the user in your backend. If the user does not exist, no attributes will be resolved. You can configure the IdP to fail the request if essential attributes are missing.
2.  **Subject Canonicalization (c14n)**: Configure a c14n flow that verifies the principal against your user store.

### Remember Me:
**You can let users skip the privacyIDEA second factor on a trusted device.**<br>
When `privacyidea.remember_me_enabled=true` (and a `privacyidea.api_key` is configured), a "remember this device" checkbox is shown on the privacyIDEA form. If the user ticks it and authentication succeeds, privacyIDEA issues a persistent-device cookie. On later logins from that browser the plugin presents the cookie to privacyIDEA, which recognises the device so the second factor can be skipped — for as long as the **server-side** policy allows.

The logic lives in privacyIDEA, not in the plugin: the plugin is only the transport. This requires **privacyIDEA 3.14+** with a `remember_device` policy (scope `authentication`) enabled for this client. See privacyIDEA's own documentation for the policy, validity and counter options.

How it works and why it is safe:
- **API client identity.** The feature is tied to an API key (`privacyidea.api_key`) that identifies this plugin to privacyIDEA and gates the feature per client. The key is sent as the `X-API-Key` header; it is never placed in the cookie. An admin can revoke or rotate it server-side at any time.
- **Rotating token, not a bearer secret.** The cookie (`pi_remember_device`) is a rotating `series:counter` token. privacyIDEA advances it on every use and returns the new value, which the plugin stores. If a cookie is cloned, the stale copy is detected on next use and privacyIDEA destroys the whole session series — cutting off attacker and legitimate device alike and forcing a fresh full login. (Rotation makes theft *detectable*; it is not a device-bound credential, so it does not by itself make theft impossible — see the security note below.)
- **Only ever the second factor.** Recognition can, at most, skip the privacyIDEA step. It requires an **active first-factor result for the same user** in the current login (e.g. a preceding `authn/Password`, or a still-valid IdP SSO session). With no active first factor the cookie is ignored and the user is challenged normally, so it is **ignored in standalone / passkey-only mode** (where privacyIDEA is the only factor) — the checkbox is not shown there.
- **SSO / forced re-auth.** As with any SSO login, when an IdP session is already active and the relying party does not request re-authentication, the first factor is satisfied by that existing session rather than re-prompted. If you need the first factor re-proven for a sensitive service, request `forceAuthn` (or set an authentication `maxAge`) on that relying party: there is then no active first-factor result to reuse, so the remember-me skip does not apply and the second factor is enforced as well.
- **Cookie attributes.** The plugin sets the cookie `HttpOnly` and `Secure`. Its `SameSite` attribute is governed by the IdP's global cookie policy (`idp.cookie.sameSite`, default `None`), not set per-cookie by the plugin — set that property to `Strict`/`Lax` if you want to constrain it. Its lifetime is **server-driven**: the plugin applies the `Max-Age` privacyIDEA sends on its cookie, so the browser cookie expires exactly per the server-side `remember_device` policy — there is no client-side day setting. (If privacyIDEA sends no `Max-Age`, the plugin falls back to a 7-day persistent cookie.)

> **Security note.** A remembered device is a deliberate, bounded relaxation of MFA, not a free one. Token rotation gives you *detection* of a stolen cookie, but between rotations the cookie is still a bearer token — it is not bound to the device the way a passkey is. Enable it where reducing second-factor friction is worth that trade-off, and rely on the server-side policy (max age, counters) and per-client key revocation to bound the exposure.

Configure it with `privacyidea.api_key` and `privacyidea.remember_me_enabled` — see the table below. The cookie name and lifetime are owned by privacyIDEA, so there is nothing else to set on the plugin side.

### Requested authentication context (AuthnContextClassRef):
By default the privacyIDEA flow advertises no specific SAML `AuthnContextClassRef`. If a service provider sends a `RequestedAuthnContext` — or you need the IdP to assert a particular context class, e.g. the REFEDS MFA profile for eduGAIN / DFN-AAI — declare the values the flow can satisfy via `idp.authn.privacyIDEA.supportedPrincipals` in `privacyidea.properties`:

```
idp.authn.privacyIDEA.supportedPrincipals = https://refeds.org/profile/mfa,https://refeds.org/profile/sfa
```

Shibboleth then selects the privacyIDEA flow when an SP requests one of these classes and asserts the matched class back in the response. The value is comma-separated; leave it unset for no specific context class.

The `privacyIDEA2` flow inherits this (and the other flow-descriptor settings — `order`, `lifetime`, `reuseCondition`, …) from the `privacyIDEA` keys by default. To give the second flow a different context class, set its own `idp.authn.privacyIDEA2.supportedPrincipals` in `privacyidea2.properties`; each `idp.authn.privacyIDEA2.*` key falls back to the matching `idp.authn.privacyIDEA.*` value.

### Configuration Parameters for privacyIDEA Plugin:
An example of the privacyIDEA plugin configuration can be found in *privacyidea.properties* (`$idp_install_path/conf/authn/privacyidea.properties`).
The different configuration parameters are explained in the following table:

| Configuration                        | Explanation                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|--------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `privacyidea.server_url`             | The URL of your privacyIDEA server. This must be reachable from the Shibboleth IdP server.                                                                                                                                                                                                                                                                                                                                                                                             |
| `privacyidea.realm`                  | This realm will be appended to all requests to the privacyIDEA. <br/>Note: Drop it to use the default realm.                                                                                                                                                                                                                                                                                                                                                                           |
| `privacyidea.verify_ssl`             | Choose if the Shibboleth should verify the SSL certificate of the privacyIDEA. <br/>Note: Always verify the SSL certificate in a productive environment!                                                                                                                                                                                                                                                                                                                               |
| `privacyidea.http_timeout_ms`        | HTTP timeout for all privacyIDEA requests, in milliseconds. Only digits allowed; a blank/invalid value keeps the default. Default `10000` (10s).                                                                                                                                                                                                                                                                                                                                        |
| `privacyidea.default_message`        | Use this parameter to edit the default user message.                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `privacyidea.otp_field_hint`         | Use this parameter to edit the default placeholder for the OTP input field.                                                                                                                                                                                                                                                                                                                                                                                                            |
| `privacyidea.authentication_flow`    | Choose one of three possible options:<br>`default` - standard authentication flow,<br>`triggerChallenge` - triggers all challenges beforehand using the provided service account. Required additional parameters: *privacyidea.service_name*, *privacyidea.service_pass* (see below),<br>`sendStaticPass` - performs the privacyIDEA server request automatically beforehand using the provided static password. Required additional parameter: *privacyidea.static_pass* (see below). |
| `privacyidea.service_name`           | The username of the service account required by the `triggerchallenge` config option. <br/>Note: Please make sure, that the service account has the correct rights.                                                                                                                                                                                                                                                                                                                    |
| `privacyidea.service_pass`           | The password of your service account, which is required by the `triggerchallenge` config option.                                                                                                                                                                                                                                                                                                                                                                                       |
| `privacyidea.service_realm`          | Specify a separate service account's realm if needed. <br/>Note: If the service account is located in the same realm as the users, it is sufficient to specify the realm in the `privacyidea.realm` parameter.                                                                                                                                                                                                                                                                         |
| `privacyidea.static_pass`            | The password which should be used in the `sendStaticPass` authentication flow. <br/>Note: You can also leave it empty to perform the privacyIDEA server request with an empty pass (useful in some scenarios).                                                                                                                                                                                                                                                                         |
| `privacyidea.forward_headers`        | Set the headers that should be forwarded to the privacyIDEA. <br/>Note: If some header doesn't exist or has no value, will be ignored. <br/>Note: The header names should be separated by a comma (",").                                                                                                                                                                                                                                                                               |
| `privacyidea.otp_length`             | If you want to turn on the form-auto-submit function after x number of characters are entered into the OTP input field, set the expected OTP length here. <br/>Note: Only digits as the parameter's value are allowed here.                                                                                                                                                                                                                                                            |
| `privacyidea.polling_interval`       | Decide after how many seconds the form should be reloaded, to check if the push token was confirmed. Default is 2.                                                                                                                                                                                                                                                                                                                                                                     |
| `privacyidea.polling_in_browser`     | Enable this to do the polling for accepted push requests in the user's browser. When enabled, the login page does not refresh to confirm the push authentication. CORS settings for privacyidea can be adjusted in etc/apache2/sites-available/privacyidea.conf.                                                                                                                                                                                                                       |
| `privacyidea.polling_in_browser_url` | If 'poll in browser' should use a deviating URL, set it here. Otherwise, the general URL will be used.                                                                                                                                                                                                                                                                                                                                                                                 |
| `privacyidea.disable_passkey`        | Set to 'true' to disable passkey authentication.                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `privacyidea.skip_first_step`        | Default `true`. When `true`, the plugin's own username/password form is skipped if a prior MFA sub-flow (e.g. `authn/Password`) produced a fresh authentication result in the current MFA run, and that result's principal is used. If no fresh result exists (e.g. privacyIDEA is the first factor, or only a stale session principal is available), the form is displayed regardless — prefilled with the principal if one is known. Set to `false` to always display the form.      |
| `privacyidea.api_key`                | API key identifying this plugin to privacyIDEA (`pi_<key_id>_<secret>`), obtained from the privacyIDEA admin. **Required** for remember-me; sent as the `X-API-Key` header. Keep it secret (see [Securing the configuration](#securing-the-configuration)).                                                                                                                                                                                                                              |
| `privacyidea.remember_me_enabled`    | Default `false`. Set to `true` (with `api_key` set) to show a "remember this device" checkbox on the privacyIDEA form. When checked and authentication succeeds, privacyIDEA issues a rotating `pi_remember_device` cookie; on later logins the plugin presents it and privacyIDEA can skip the second factor per its `remember_device` policy. Requires **privacyIDEA 3.14+**. Only applies when a preceding first factor authenticated the user — **ignored in standalone mode**. See the [Remember Me](#remember-me) section. |
| `privacyidea.debug`                  | Set this parameter to true to see the debug messages in the `idp-process.log`.                                                                                                                                                                                                                                                                                                                                                                                                         |

### Securing the configuration:
`privacyidea.properties` can contain secrets — the service-account password (`privacyidea.service_pass`) and, for the remember-me feature, the client API key (`privacyidea.api_key`). Restrict it so only the IdP service account can read it, e.g. on Linux:

```
chown root:<idp-group> conf/authn/privacyidea.properties
chmod 640 conf/authn/privacyidea.properties
```

Adjust owner/group to match how your IdP runs (e.g. the Tomcat/Jetty service user). The API key is revocable/rotatable server-side in privacyIDEA (`/clients/<id>/rotate`) if it is ever exposed.

### Log check:
- **Main log: `$idp_install_path/logs/idp-process.log`.**
- **Warn and error log: `$idp_install_path/logs/idp-warn.log`.**

### Plugin update:
**To update the plugin, repeat the installation process with the new archive data.<br>**
If something goes wrong, check if some of the plugin files have their .idpnew copies,<br>
remove the .idpnew copies, and re-run the installation process.

### 2nd authentication flow:
**If you want to set up a second authentication flow (e.g. for another privacyIDEA server or realm):<br>**
- Add a new subflow called `authn/privacyIDEA2` to the mfa-authn-config.xml file.
Note: Obviously, you need to adjust the flow transition map to your needs.
- Example of the MFA flow configuration based on IP check: [MFA Config Example](docs/mfaConfigExample).
- Copy the `privacyidea.properties` file to `privacyidea2.properties`.
- Update the `privacyidea2.properties` file with the new configuration data.
Note: Make sure to change each configuration variables names to `privacyidea2.*` in the `privacyidea2.properties` file.
- Restart the Shibboleth IdP server to apply the changes and register new auth flow.

### Translation:
If you want to translate the plugin, you can use the `messages.properties` file located in `$idp_install_path/conf/authn/messages.properties`.<br>
See shibboleth documentation for more information about the translation process: [Shibboleth - Translation](https://shibboleth.atlassian.net/wiki/spaces/IDP30/pages/2499314036/MessagesTranslation).
