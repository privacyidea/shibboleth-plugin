# shibboleth-plugin

### Min. shibboleth-idp version:
4.3.x

### Installation:
1. **Copy the package to the server:**
    - Chose the archive type for your system from the release page (.zip, .tar.gz, .tar.bz2).
    - Copy it with the corresponding signature data (.asc).
    - Place both files somewhere in your shibboleth server.
2. **Open the terminal and run: `sudo -i` for the admin rights.**
3. **Enable the MFA Module: `$idp_install_path/bin/module.sh -t idp.authn.MFA || $idp_install_path/bin/module.sh -e idp.authn.MFA`.**
4. **Check if the MFA Module is activated: `$idp_install_path/bin/module.sh -l`.**
5. **Install the privacyIDEA Plugin: `$idp_install_path/bin/plugin.sh -i path/to/zip/from/step/1 --noCheck`**<br>
Note: The installer will automatically install and enable the privacyIDEA Module. You can check it state by repeating the step 4.
6. **Check if the privacyIDEA Plugin is installed correctly: `$idp_install_path/bin/plugin.sh -l`**<br>
Note: If the *org.privacyidea.privacyIDEA* is on the list, the plugin was installed successfully!<br>
Otherwise, re-run the command from step 5 with `--verbose`.

### Configuration:
1. **Update the *privacyidea.properties* file (`$idp_install_path/conf/authn/privacyidea.properties`) by adding your own configuration data.**<br>
Updating following parameters is required to ensure at least the very basic functionality:
   - `privacyidea.server_url`
   - `privacyidea.verify_ssl`
   - `privacyidea.authentication_flow`

2. **Add the privacyIDEA subflow to the MFA flow.**<br>
   - Path to the MFA flow configuration file: `$idp_install_path/conf/authn/mfa-authn-config.xml`.
   - Example of the *util:map* is located in the *privacyidea.properties* file (`$idp_install_path/conf/authn/privacyidea.properties`).
   - Remember to activate the MFA flow.

3. **Turn on the MFA Module by updating following file: `$idp_install_path/conf/authn/authn.properties`.**<br>
Note: Example of this configuration contains the *privacyidea.properties* file (`$idp_install_path/conf/authn/privacyidea.properties`).

The different configuration parameters are explained in the following table:

| Configuration                     | Explanation                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|-----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `privacyidea.server_url`          | The URL of your privacyIDEA server. This must to be reachable from the Shibboleth IdP server.                                                                                                                                                                                                                                                                                                                                                                                          |
| `privacyidea.realm`               | This realm will be appended to all requests to the privacyIDEA. <br/>Note: Drop it to use the default realm.                                                                                                                                                                                                                                                                                                                                                                           |
| `privacyidea.verify_ssl`          | Choose if the Shibboleth should verify the SSL certificate of the privacyIDEA. <br/>Note: Always verify the SSL certificate in a productive environment!                                                                                                                                                                                                                                                                                                                               |
| `privacyidea.default_message`     | Use this parameter to edit the default user message.                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `privacyidea.otp_field_hint`      | Use this parameter to edit the default placeholder for the OTP input field.                                                                                                                                                                                                                                                                                                                                                                                                            |
| `privacyidea.authentication_flow` | Choose one of three possible options:<br>`default` - standard authentication flow,<br>`triggerChallenge` - triggers all challenges beforehand using the provided service account. Required additional parameters: *privacyidea.service_name*, *privacyidea.service_pass* (see below),<br>`sendStaticPass` - performs the privacyIDEA server request automatically beforehand using the provided static password. Required additional parameter: *privacyidea.static_pass* (see below). |
| `privacyidea.service_name`        | The username of the service account required by the `triggerchallenge` config option. <br/>Note: Please make sure, that the service account has the correct rights.                                                                                                                                                                                                                                                                                                                    |
| `privacyidea.service_pass`        | The password of your service account required by the `triggerchallenge` config option.                                                                                                                                                                                                                                                                                                                                                                                                 |
| `privacyidea.service_realm`       | Specify a separate service account's realm if needed. <br/>Note: If the service account is located in the same realm as the users, it is sufficient to specify the realm in the `privacyidea.realm` parameter.                                                                                                                                                                                                                                                                         |
| `privacyidea.static_pass`         | The password which should be use in the `sendStaticPass` authentication flow. <br/>Note: You can also leave it empty to perform the privacyIDEA server request with empty pass (useful in some scenarios).                                                                                                                                                                                                                                                                             |
| `privacyidea.forward_headers`     | Set the headers that should be forwarded to the privacyIDEA. <br/>Note: If some header doesn't exist or has no value, will be ignored. <br/>Note: The header names should be separated by a comma (",").                                                                                                                                                                                                                                                                               |
| `privacyidea.otp_length`          | If you want to turn on the form-auto-submit function after x number of characters are entered into the OTP input field, set the expected OTP length here. <br/>Note: Only digits as the parameter's value allowed here.                                                                                                                                                                                                                                                                |
| `privacyidea.polling_interval`    | Decide after how many seconds the form should be reloaded, to check if the push token was confirmed. Default is 2.                                                                                                                                                                                                                                                                                                                                                                     |
| `privacyidea.debug`               | Set this parameter to true to see the debug messages in the `idp-process.log`.                                                                                                                                                                                                                                                                                                                                                                                                         |

### Log check:
- **Main log: `$idp_install_path/logs/idp-process.log`.**
- **Warn and error log: `$idp_install_path/logs/idp-warn.log`.**

### Plugin update:
**To update the plugin, repeat the installation process with the new archive data.**
