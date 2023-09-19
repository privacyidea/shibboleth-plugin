# shibboleth-plugin

### Min. shibboleth-idp version:
4.3.x

### Installation:
1. **Copy the package to the server:**
    - Chose the archive type for your system from the release page (.zip, .tar.gz, .tar.bz2).
    - Copy it with the corresponding signature data (.asc).
    - Place both data somewhere in your shibboleth server.
2. **Open the terminal and run: `sudo -i` for admin rights.**
3. **Enable the MFA Module: `$idp_install_path/bin/module.sh -t idp.authn.MFA || $idp_install_path/bin/module.sh -e idp.authn.MFA`.**
4. **Check if the MFA Module is activated: `$idp_install_path/bin/module.sh -l`.**
5. **Install the privacyIDEA Plugin: `$idp_install_path/bin/plugin.sh -i path/to/zip/from/step/1 --noCheck`.**
6. **Check if the privacyIDEA Plugin is installed correctly: `$idp_install_path/bin/plugin.sh -l`.**<br>
If you see the *org.privacyidea.privacyIDEA* on the list, the plugin is installed successfully.<br>
Otherwise, re-run the command from step 5.

### Configuration:
1. **Create the *privacyidea.properties* file in: `$idp_install_path/config/authn/`.**<br>
   You can find a template in the resources in this repo: `/privacyIDEA-impl/src/resources/org/privacyidea/conf/authn/`.<br>
   The config file should contain the following configuration variables:
   - `privacyidea.server_url=https://localhost`
   - `privacyidea.realm=defrealm`
   - `privacyidea.verify_ssl=true`
   - `privacyidea.default_message=Please enter the OTP`
   - `privacyidea.otp_field_hint=OTP`
   - `privacyidea.triggerchallenge=false`
   - `privacyidea.service_name=service`
   - `privacyidea.service_pass=service`
   - `privacyidea.service_realm=defrealm`
   - `privacyidea.forward_headers=header1,header2,header3`
   - `privacyidea.debug=false`

2. **Add the privacyIDEA subflow to the MFA flow.**<br>
   - Path to the MFA flow configuration file: `$idp_install_path/conf/authn/mfa-authn-config.xml`.
   - Example of the *util:map* is located in the resources at the end of the *privacyidea.properties* file (`/privacyIDEA-impl/src/resources/org/privacyidea/conf/authn/privacyidea.properties`).
   - Remember to activate the MFA flow.

### Log check:
- **Main log: `$idp_install_path/logs/idp-process.log`.**
- **Warn and error log: `$idp_install_path/logs/idp-warn.log`.**

### Plugin update:
To update the plugin, repeat the installation process with the new archive data.