# Pleos Connect emulator setup

Source: https://document.pleos.ai/docs/pleos-only/getting-started and
https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
(fetch the second page for the exact update-site URL; it may change with emulator releases).

1. Android Studio › Settings › Languages & Frameworks › Android SDK › **SDK Update Sites** › add the Pleos Connect system image repository.
2. **SDK Platforms** › show package details › install the Pleos Connect **API 34** system image.
3. Device Manager › Create Virtual Device › category **Automotive** › pick the Pleos Connect image.
4. Advanced: Boot = **Cold boot**, RAM = **4 GB**.
5. Start it and wait for full boot before installing the app.
6. Release notes: /docs/pleos-only/release-note/ (emulator versions; CRN no longer required from Emulator 2.1.0).
