# PriceAlert project handoff

## Product goals

- PriceAlert is the repository name. Drop Price is the Android app name.
- Build an original, modern price tracker with the functionality discussed for DropAlert, plus multiple store URLs on one product and import from the Android Share menu. Verify the remaining DropAlert feature inventory before claiming parity.
- Keep the app fast and lightweight in storage, memory, and CPU use. Reuse the existing native Android implementation and add dependencies only when justified.
- Use the user's GitHub repository `iamshuvajitdey/PriceAlert`. Commit project changes and report the commit and verification status. Give brief milestone updates during active work.

## Repository and current state

- Android application ID: `com.dropprice.app`. Kotlin, Android Gradle Plugin 8.7.3, Kotlin plugin 2.0.21, JDK 17, compile/target SDK 35, minimum SDK 26. CI uses Gradle 8.9.
- `app/src/main/java/com/dropprice/app/MainActivity.kt` contains the prototype UI, local watchlist, and price extraction. The manifest registers Android text sharing. The watchlist stores products and offer URLs in SharedPreferences.
- Implemented in source: local watchlist, multiple HTTPS store links per product, Android Share import into a new or existing product, and a manual price check. The price check currently expects a single usable schema.org JSON-LD offer. Broader retailer compatibility remains unverified.
- The prototype is committed in `1534ad5`; `8d6ac11` adjusts window insets for older Android versions. Commit `0bdc16b` adds the Android build workflow.
- The first GitHub Actions build passed and uploaded the `drop-price-debug` artifact containing `app-debug.apk`. [Build run](https://github.com/iamshuvajitdey/PriceAlert/actions/runs/45961457690)
- Phone installation and on-device behavior remain unverified. Price history and background price alerts are outstanding. The completion watch was configured to notify after an installable APK and verification of the core flows. A successful build alone does not satisfy that condition.

## Laptop and phone testing

- The user prefers development and testing from their laptop. Prior work ran in a cloud workspace, and GitHub Actions built the APK. Start in a local checkout and confirm the session can execute commands on the laptop. Ask before installing anything on the laptop. Check for tools already installed and keep any approved setup minimal. Android Studio is optional.
- To build locally with the toolchain above, run `gradle :app:assembleDebug` from the repository root. The output is `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions can supply the existing debug APK if testing does not require a new build.
- The user enabled Android Wireless debugging and prefers that installation path. Use ADB from the laptop on the same Wi-Fi. Start with `adb version`. If ADB is missing, ask before installing Android SDK Platform Tools. Use a fresh code from **Pair device with pairing code** and enter it locally when prompted by `adb pair <phone-ip>:<pairing-port>`. Pairing codes are transient and belong outside chat, logs, and version control.
- Check `adb devices -l`. If the phone does not appear automatically, run `adb connect <phone-ip>:<debugging-port>` using the port on the main Wireless debugging screen. The pairing and debugging ports may differ. Target the phone explicitly with `adb -s <device-serial> install -r <path-to-apk>`, then open Drop Price and verify installation.
- Smoke-test Android Share import, adding a second store URL to the same product, persistence after restart, and manual price checks with supported and unsupported pages. Then implement and verify price history and background alerts, including the notification permission flow where required. Record the device, build, and results before calling the app ready.
