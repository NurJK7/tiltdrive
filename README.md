# TiltDrive Remote

Phone-as-steering-wheel for BeamNG.drive. Kotlin + Jetpack Compose, minSdk 26, targetSdk 36.
Unofficial: not affiliated with BeamNG (see NOTICE.txt).

## Run
1. Android Studio (latest stable) -> File -> Open -> this folder. Accept "Update Gradle/AGP" if offered.
2. Phone: enable Developer options + USB debugging, plug in, press Run.
3. PC and phone on the same Wi-Fi. In the game open the remote controller, show the QR, tap "Scan QR code".

## Send a test build to anyone
Build -> Build APK(s) -> app/build/outputs/apk/debug/app-debug.apk, send the file. They must allow installing from unknown sources.

## If something is off
- Steering goes the wrong way: Settings -> Reverse steering.
- Not connecting: logcat/Wireshark should show UDP "beamng|<phone>|<code>" to port 4444 and a reply "beamng|<code>" to port 4445.
- Turn indicators/handbrake lamps wrong: edit the `Lights` bit values in Protocol.kt.

## Publish to Google Play
1. Rename: package in app/build.gradle.kts (applicationId), app name, icon. Do NOT use "BeamNG" or com.beamng.
2. Build -> Generate Signed App Bundle (.aab), create and BACK UP your keystore.
3. Play Console (one-time $25), upload the .aab, fill in the Data safety form (the app collects no data), content rating.
4. Store description: say it is unofficial and link https://github.com/BeamNG/remotecontrol.
5. New personal accounts must run a closed test first; check current Google rules.
