## 1.2.0 07/2025

### Features
* Passkey support
* Possibility to choose between 2 different privacyIDEA configurations or instances
* Translation support by using messages.properties
* Possibility to use privacyIDEA in standalone mode (without password module)
* Improved UI and UX

### Fixes
* Display link for token enrollment in UI
* Use Feth API for polling in browser (push token)
* Implemented restart button to the authentication form

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