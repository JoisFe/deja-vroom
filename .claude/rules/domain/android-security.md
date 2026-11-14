---
paths:
  - "app/**"
---

# Domain: Android app security (must follow)

Sources: Pleos app security guide (official Pleos docs), Android security best practices. These cover the contest release APK and public GitHub repo; App Market listing (winners only) adds more in that doc.

## Secrets and credentials
- No secrets in the APK: no API keys, Pleos Client Secret, tokens, or passwords in code, resources, `BuildConfig`, or `local.properties` values compiled into the app. `BuildConfig` fields are readable by anyone with the APK.
- TTS/STT server modes needing Client ID/Secret: fetch short-lived credentials from our backend, or use `ON_DEVICE` mode. If a value must be injected at build time, it is treated as public.
- Tokens at rest: encrypt with an Android Keystore key (e.g. Tink AEAD with a Keystore master key) before storing in DataStore. Don't add `EncryptedSharedPreferences` (androidx.security-crypto is deprecated).

## Network
- HTTPS only: `android:usesCleartextTraffic="false"` and a `network_security_config.xml` with `cleartextTrafficPermitted="false"`. TLS 1.2+; never trust-all `TrustManager`/`HostnameVerifier`.
- Use domain names, not IPs. No tokens or personal data in URLs or query strings.
- Validate and bound every server response and SDK callback value (nullable, ranges) before use.

## Components and IPC
- Every `<activity|service|receiver|provider>` declares `android:exported`; `false` unless another app must start it (launcher activity, Gleo-targeted components).
- Explicit intents for internal communication; sensitive broadcasts use an explicit package/component or a permission.
- `PendingIntent` with `FLAG_IMMUTABLE` unless mutability is required.
- No WebView in the contest app. If one becomes necessary, apply the Pleos security guide's WebView items first.

## Permissions and privacy
- Declare only the `pleos.car.permission.*` and Android permissions the app actually uses; request dangerous ones (location, mic) at the moment of use with a reason shown.
- Collect the minimum personal and vehicle data (VIN, location, voice); don't send VIN/location to servers unless the feature requires it and the privacy policy covers it.
- Logs: no personal data, tokens, VIN, precise location, or raw voice text in logs. Release builds strip debug logging.

## Build and release
- Release: `isMinifyEnabled = true`, not debuggable, signed with the release key (never the debug key). Keystore files and passwords stay out of git (env vars / `secrets.properties`).
- `android:allowBackup="false"` (or backup rules that exclude credentials and personal data).
- WebView remote debugging and StrictMode penalties only in debug builds.
- Use only official, documented Pleos/AAOS APIs; no reflection into hidden APIs.
- Dependencies from `google()`, `mavenCentral()`, and the Pleos Nexus only; pin versions in the catalog.
