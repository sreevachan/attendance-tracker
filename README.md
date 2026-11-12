# Attendance Tracker (iOS)

A simple iPhone app to track college attendance, built with SwiftUI,
a WKWebView-based HTML/JS UI, and a WidgetKit home-screen widget.

## Features
- Monthly calendar: mark days Present / Absent / Partly / Holiday
- Overall and monthly attendance percentage vs the 75% minimum
- "Plan ahead" calculator: how many classes you can still miss
- Home-screen widget showing current percentage and progress bar

## How it works
The UI is `index.html` running inside a WKWebView. On every update the page
sends the totals to Swift, which saves them to an App Group so the widget
can read them.

## Setup
(Xcode 15+, iOS 17+)
1. Open the project in Xcode and select your Apple ID team for both targets.
2. Add the App Groups capability to both targets with the same ID.
3. Put that ID in `WebView.swift` and `AttendanceWidget.swift`.
4. Run on your iPhone, then add the widget from the home screen.

## Roadmap
- Store class count per day so timetable changes don't alter history
- Backup/export of data
- Android version

<img width="1290" height="2448" alt="IMG_2613" src="https://github.com/user-attachments/assets/9f1f1434-72c1-4b2d-931a-1f2ea794f822" />
<img width="1290" height="1821" alt="IMG_2614" src="https://github.com/user-attachments/assets/5746c75c-ea9b-42d9-9041-ac68c49eee5c" />
<img width="555" height="550" alt="IMG_2617" src="https://github.com/user-attachments/assets/bf6b9965-1def-43a4-991e-0039e6d1a185" />
