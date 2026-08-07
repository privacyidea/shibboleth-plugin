## Unreleased

### Features
* "Remember this device": optional checkbox that lets a trusted device skip the privacyIDEA second
  factor. The plugin acts as a privacyIDEA API client (`privacyidea.api_key`): on opt-in the server
  issues a rotating `pi_remember_device` cookie, and on later logins the plugin presents it so the
  server can recognise the device. The token rotates on every use (theft detection) and privacyIDEA
  owns the policy (including the cookie lifetime — it is server-driven, not configured in the plugin).
  Requires privacyIDEA 3.14+ with a `remember_device` policy. Configure via `privacyidea.api_key` and
  `privacyidea.remember_me_enabled`. Applies only when a preceding first factor authenticated the user
  (ignored in standalone mode).
* New authentication flow `privacyidea.authentication_flow=tokenSelection`: lists the user's tokens and
  lets them choose which one to use. Triggerable tokens (push / WebAuthn / passkey) run their ceremony
  in place on the same screen (no layout switch); other tokens are used by typing their OTP. Per-token-type
  icons are configurable via the `privacyidea.tokenIcon.<type>` message keys (defaults shipped for the
  common types). Needs a service account (like `triggerChallenge`) with `tokenlist` rights.
* The OTP field placeholder ("hint") is now the `privacyidea.inputHint` message key (empty by default),
  so it is translatable per locale like the other UI labels (replaces the previous `otp_field_hint`).
* The HTTP timeout for privacyIDEA requests is configurable via `privacyidea.http_timeout_ms`.
* Bumped the bundled privacyidea-java-client to 1.6.0 (security and robustness improvements).

## 1.3.0 05/2026
Update features to be on par with privacyIDEA 3.13. Support for the following policies:
* passkey_trigger_by_pin
* enroll_via_multichallenge_optional
* push_code_to_phone

* The Accept-Language header will now always be forwarded from the browser to privacyIDEA.

## 1.2.0 07/2025

### Features
* Passkey support
* Possibility to choose between 2 different privacyIDEA configurations or instances
* Translation support by using messages.properties
* Possibility to use privacyIDEA in standalone mode (without password module)
* Improved UI and UX

### Enhancements
* Display link for token enrollment in UI
* Use Fetch API for polling in browser (push token)
* Option to restart the authentication

## 1.1.0 02/2024

### Features
* Poll in browser

### Fixes
* Separate the css and js from the view file
* Fix the sealing violation issue occurred on jetty
* Compatibility with the IdP 5.0.0 and higher, but no longer compatible with 4.x.x. There will be separate releases for IdP 5 and 4, check the file names when downloading.

## 1.0.0 10/2023

### Features
* Multichallenge
* Enroll token via challenge
* Preferred token type
* Forwarding headers
* Auto form submit after x digits entered to the otp field

### Authentication flows
* Default
* Trigger challenges
* Send static pass

### Supported tokens
* OTP
* Push
* WebAuthn
