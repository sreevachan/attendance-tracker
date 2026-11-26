# Attendance Tracker

A native Android app, iOS app, and website to track college attendance: mark each day or individual class, track subject-wise and overall percentages, protect past records when your timetable changes, and optionally back up privately to your own Google Drive.

> Built with the help of AI tools (Claude & Gemini) as a learning project.

## Screenshots

<img width="1290" height="2448" alt="IMG_2613" src="https://github.com/user-attachments/assets/9f1f1434-72c1-4b2d-931a-1f2ea794f822" />
<img width="1290" height="1821" alt="IMG_2614" src="https://github.com/user-attachments/assets/5746c75c-ea9b-42d9-9041-ac68c49eee5c" />
<img width="555" height="550" alt="IMG_2617" src="https://github.com/user-attachments/assets/bf6b9965-1def-43a4-991e-0039e6d1a185" />

## Features
- **Subject-wise & overall tracking:** Add your subjects (e.g. English, Maths, CRT), build your Mon–Sat timetable, and view both per-subject percentages and total attendance.
- **Monthly calendar & per-class marking:** Mark an entire day Present, Absent, or Holiday in one tap, or mark individual periods (`P` / `A` / `Off`) and add extra classes.
- **Timetable history protection:** Changing your timetable mid-semester (e.g., 6 classes/day to 7 classes/day) applies from the date you choose—past marked and unmarked days stay untouched.
- **Custom start date & manual entry:** Start from `0 / 0` on your college start date, or enter manual / mid-semester numbers per subject (or work out conducted classes from your percentage).
- **Year, Semester, Mid & semester reset:** Track your current Year, Semester, and Mid, and start a new semester at `0` while archiving past semesters in history.
- **Plan ahead calculator:** See how many classes remain until your exam/target date and how many you can still miss while staying above your minimum (default `75%`).
- **Home-screen widgets:** Native home-screen attendance widget on Android (Jetpack Glance) and iPhone.
- **In-app APK updates:** Android APK users get a banner when a new release is out and can download & install it directly inside the app.
- **Light, dark, or system theme.**
- **Private Google Drive backup (optional):** Use offline without an account, or sign in with Google to back up into **your own Google Drive** (`appDataFolder`):
  - **End-to-end encrypted (recommended):** Encrypted on-device with AES-256-GCM before upload, protected by your passphrase and recovery code.
  - **Standard Drive backup:** Restores automatically when you sign in with Google, without a passphrase.
  - **None:** Everything stays only on your device.

## Privacy and security
- **No developer server or user list:** On Android and the website, backups are saved directly into **your own hidden Google Drive app folder** (`drive.appdata`). The developer cannot see a list of users or access your data.
- **Zero-knowledge encryption:** Encrypted backups use `AES-256-GCM` with keys wrapped via `PBKDF2-HMAC-SHA256` (310,000 iterations for passphrase, 100,000 for recovery code). If you lose both, nobody can recover the encrypted backup.
- **Offline mode:** Without an account, everything stays locally on your device.
- **No analytics or trackers:** Deleting your backup removes `attendance-backup.json` from your Google Drive app folder and wipes local data on the device.
- Signing keystores and platform config secrets (`GoogleService-Info.plist`, `google-services.json`, `*.jks`, `*.keystore`) are excluded from this repository.

## Download & Use It
- **Android (Native App — Recommended):**
  1. Download [`app-release.apk`](https://github.com/sreevachan/attendance-tracker/releases/latest/download/app-release.apk) from the [Releases](https://github.com/sreevachan/attendance-tracker/releases/latest) page.
  2. Tap the downloaded `.apk` file on your Android phone and tap **Install** (allow **Install unknown apps** if Android asks).
  3. Future updates can be installed directly from the **Update available** banner inside the app.
- **Website (All Devices):**
  - Open https://sreevachan.github.io/attendance-tracker/ (or https://attendance-app-4d857.firebaseapp.com/).
  - On **iPhone**, tap Share → **Add to Home Screen** in Safari. On **Android/Desktop Chrome**, tap **Install app**.
- **iPhone (Native App):** Installed privately from Xcode by the developer (`ios/`).

## Project structure
- `android/`: Native Kotlin + Jetpack Compose Android app & Glance home-screen widget
- `docs/`: Website (published via GitHub Pages & Firebase Hosting)
- `ios/`: Xcode project for the native iPhone app (private distribution)
- `version.json`: Release metadata checked by the apps for updates

## Building the Android APK from source
1. Install JDK 17 and the Android SDK (API 36).
2. Run:
   ```zsh
   cd android
   ./gradlew testDebugUnitTest assembleRelease
   ```
3. The APK is generated at `android/app/build/outputs/apk/release/app-release.apk`.

## Roadmap
- Change passphrase, and biometric / Face ID unlock
- Photo import of attendance sheets

## Version history
- **v2.1.0:** Native Android APK & Glance widget, Google Drive `appDataFolder` backup (zero user list on developer servers), subject-wise timetable & attendance, timetable history (`effectiveFrom`), custom college start date, manual entry, Year/Sem/Mid & semester reset, direct in-app APK updater
- **v2.0:** Google sign-in, optional encrypted or standard backup, settings, welcome screen, update notice, app icon, website
- **v1.1:** Widget fix
- **v1.0:** First version: calendar, planner, and widget

Made by Sreevachan.
