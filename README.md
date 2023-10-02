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
6. **Check if the privacyIDEA Plugin is installed propertly: `$idp_install_path/bin/plugin.sh -l`**<br>
Note: If the *org.privacyidea.privacyIDEA* is on the list, the plugin was installed successfully!<br>
Otherwise, re-run the command from step 5 with `--verbose`.

### Configuration:
1. **Update the *privacyidea.properties* file (`$idp_install_path/config/authn/privacyidea.properties`), by adding your own configuration data.**<br>
The config file should contain the following configuration variables:
   - `privacyidea.server_url`
   - `privacyidea.realm`
   - `privacyidea.verify_ssl`
   - `privacyidea.default_message`
   - `privacyidea.otp_field_hint`
   - `privacyidea.triggerchallenge`
   - `privacyidea.service_name`
   - `privacyidea.service_pass`
   - `privacyidea.service_realm`
   - `privacyidea.forward_headers`
   - `privacyidea.debug`

2. **Add the privacyIDEA subflow to the MFA flow.**<br>
   - Path to the MFA flow configuration file: `$idp_install_path/conf/authn/mfa-authn-config.xml`.
   - Example of the *util:map* is located in the *privacyidea.properties* file (`$idp_install_path/conf/authn/privacyidea.properties`).
   - Remember to activate the MFA flow.

3. **Turn on the MFA Module by updating following file: `$idp_install_path/conf/authn/authn.properties`.**<br>
Note: Example of this configuration contains the *privacyidea.properties* file (`$idp_install_path/config/authn/privacyidea.properties`).

The different configuration parameters, are explained in the following table:

| Configuration                  | Explanation                                                                                                                                                                                                                      |
|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `privacyidea.server_url`       | The URL of your privacyIDEA server. This must to be reachable from the Shibboleth IdP server.                                                                                                                                    |
| `privacyidea.realm`            | This realm will be appended to all requests to the privacyIDEA. <br/>Note: Drop it to use the default realm.                                                                                                                     |
| `privacyidea.verify_ssl`       | Choose if the Shibboleth should verify the SSL certificate of the privacyIDEA. <br/>Note: Always verify the SSL certificate in a productive environment!                                                                         |
| `privacyidea.default_message`  | Use this parameter to edit the default user message.                                                                                                                                                                             |
| `privacyidea.otp_field_hint`   | Use this parameter to edit the default placeholder for the OTP input field.                                                                                                                                                      |
| `privacyidea.triggerchallenge` | Set this to true, if all challenges should be triggered beforehand using the provided service account. <br/>Note: This config option require to update the `privacyidea.service_name` and `privacyidea.service_pass` parameters. |
| `privacyidea.service_name`     | The username of the service account required by the `triggerchallenge` config option. <br/>Note: Please make sure, that the service account has the correct rights.                                                              |
| `privacyidea.service_pass`     | The password of your service account required by the `triggerchallenge` config option.                                                                                                                                           |
| `privacyidea.service_realm`    | Specify a separate service account's realm if needed. <br/>Note: If the service account is located in the same realm as the users, it is sufficient to specify the realm in the `privacyidea.realm` parameter.                   |
| `privacyidea.forward_headers`  | Set the headers that should be forwarded to the privacyIDEA. <br/>Note: If some header doesn't exist or has no value, will be ignored. <br/>Note: The header names should be separated by a comma (",").                         |
| `privacyidea.otp_length`       | If you want to turn on the form-auto-submit function after x number of characters are entered into the OTP input field, set the expected OTP length here. <br/>Note: Only digits as the parameter's value allowed here.          |
| `privacyidea.debug`            | Set this parameter to true to see the debug messages in the `idp-process.log`.                                                                                                                                                   |

### Log check:
- **Main log: `$idp_install_path/logs/idp-process.log`.**
- **Warn and error log: `$idp_install_path/logs/idp-warn.log`.**

### Plugin update:
**To update the plugin, repeat the installation process with the new archive data.**