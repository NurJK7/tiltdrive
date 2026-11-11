# TiltDrive Remote

Phone-as-steering-wheel for BeamNG.drive. Kotlin + Jetpack Compose, minSdk 26, targetSdk 36.
Unofficial: not affiliated with BeamNG (see NOTICE.txt).

## Run
1. Android Studio (latest stable) -> File -> Open -> this folder. Accept "Update Gradle/AGP" if offered.
2. Phone: enable Developer options + USB debugging, plug in, press Run.
3. PC and phone on the same Wi-Fi. In the game (v0.39+): Options -> Controls -> Hardware -> "Use your phone or tablet" = Yes.
4. In the app tap AUTO-CONNECT (no QR: it finds the game by trying every 5-digit pairing code, takes up to ~20-40 s).
5. Dashboard (optional): game Options -> Advanced -> Protocols -> OutGauge: address = phone IP (shown in the app), port 4445, rate 60, then Ctrl+R.

## Send a test build to anyone
Build -> Build APK(s) -> app/build/outputs/apk/debug/app-debug.apk, send the file. They must allow installing from unknown sources.

## If something is off
- Steering goes the wrong way: Settings -> Reverse steering.
- Not connecting: allow BeamNG.drive in the Windows firewall; avoid guest Wi-Fi. Wireshark should show UDP "beamng|<phone>|<code>" to port 4444 and a reply "beamng|<code>" to port 4445.
- Dashboard empty: OutGauge is not set up in the game (see step 5).
- Lamps wrong: edit the `Lights` bit values in Protocol.kt.

## Publish to Google Play
1. Rename: package in app/build.gradle.kts (applicationId), app name, icon. Do NOT use "BeamNG" or com.beamng.
2. Build -> Generate Signed App Bundle (.aab), create and BACK UP your keystore.
3. Play Console (one-time $25), upload the .aab, fill in the Data safety form (the app collects no data), content rating.
4. Store description: say it is unofficial and link https://github.com/BeamNG/remotecontrol.
5. New personal accounts must run a closed test first; check current Google rules.
