# shibboleth-plugin

### Shibboleth Version
The main branch is compatible with IdP Version >=5.0.0.
There will always be a separate release for IdP Version 4.x.x in the release section.

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

### Configuration Parameters for privacyIDEA Plugin:
An example of the privacyIDEA plugin configuration can be found in *privacyidea.properties* (`$idp_install_path/conf/authn/privacyidea.properties`).
The different configuration parameters are explained in the following table:

| Configuration                        | Explanation                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|--------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `privacyidea.server_url`             | The URL of your privacyIDEA server. This must be reachable from the Shibboleth IdP server.                                                                                                                                                                                                                                                                                                                                                                                             |
| `privacyidea.realm`                  | This realm will be appended to all requests to the privacyIDEA. <br/>Note: Drop it to use the default realm.                                                                                                                                                                                                                                                                                                                                                                           |
| `privacyidea.verify_ssl`             | Choose if the Shibboleth should verify the SSL certificate of the privacyIDEA. <br/>Note: Always verify the SSL certificate in a productive environment!                                                                                                                                                                                                                                                                                                                               |
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
| `privacyidea.debug`                  | Set this parameter to true to see the debug messages in the `idp-process.log`.                                                                                                                                                                                                                                                                                                                                                                                                         |

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