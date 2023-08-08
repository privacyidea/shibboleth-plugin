# shibboleth-plugin

### Installation:
1. **Copy the package to the server:**
    - Go to: `/privacyIDEA-dist/target/`.
    - Find a compression data with extention which you need (.zip, .tar.gr, .tar.bz2).
    - Copy it with the corresponding signature data (.asc).
    - Place booth data somewhere in your shibboleth server.
2. **Open the terminal and run: `sudo -i` for admin rights.**
3. **Enable the MFA Module: `/bin/module.sh -t idp.authn.MFA || /bin/module.sh -e idp.authn.MFA`.**
4. **Check if the MFA Module is activated: `/bin/module.sh -l`.**
5. **Install the privacyIDEA Plugin: `/bin/plugin.sh -i path/to/files/from/step/1 --noCheck`.**
6. **Check if the privacyIDEA Plugin is installed correctly: `/bin/plugin.sh -l`.**<br>
If you see the `org.privacyidea.privacyIDEA` on the list, the plugin is installed successfully.<br>
Otherwise, re-run the command from step 5.

### Configuration:
1. **Create the `privacyidea.properties` file in: `/config/auth/`.**<br>
   You can find a template in the repo in: `/privacyIDEA-impl/src/resources/org/privacyidea/conf/authn/`.<br>
   The config file should contain the following configuration variables:
   - `privacyidea.server_url=https://localhost`
   - `privacyidea.realm=defrealm`
   - `privacyidea.verify_ssl=true`

### Log check:
- **Main log: `/logs/idp-process.log`.**
- **Warn and error log: `/logs/idp-warn.log`.**

### Plugin update:
To update the plugin, repeat the installation process with the new package data.