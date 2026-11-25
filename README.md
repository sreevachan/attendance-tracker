Attendance Tracker

A website and apps to track college attendance: mark each day, see your percentage against your minimum, and plan how many classes you can still miss. Sign in with Google for an optional backup, either end-to-end encrypted or standard.

Built with the help of AI tools (including Claude) as a learning project.


Features

	•	Monthly calendar: mark days Present, Absent, Partly present or Holiday
	•	Overall and monthly percentage against your own minimum (default 75%)
	•	"Plan ahead" calculator: how many classes you can still miss
	•	List of unmarked days, and a celebration when you reach your minimum
	•	Home-screen widget showing your percentage (native iPhone app only)
	•	Light, dark or system theme
	•	Use it without an account, or sign in with Google
	•	Optional backup you choose when you sign in:
	•	End-to-end encrypted: protected by your passphrase and a recovery code. Only you can read it.
	•	Standard: restored by signing in with Google alone. The developer could technically read it.
	•	None: your data stays on the device.

Privacy and security

	•	Without an account, everything stays on your device. If you delete the app, it is gone.
	•	Encrypted backups use AES-256-GCM, with the key wrapped by your passphrase and by a recovery code. If you lose both, nobody can recover the data.
	•	Firestore rules let each person read and write only their own document, with limited fields. See the rules below.
	•	No analytics or trackers. Account deletion also deletes the cloud backup.
	•	Firebase config files for the iPhone app (GoogleService-Info.plist) are not in this repository.

Use it

	•	iPhone: use the website below. In Safari, tap Share, then Add to Home Screen to get an icon. The native iPhone app is not available to the public. It is installed only on the developer's own phone and on phones of people he personally gives access to from his Mac.
	•	Android: download the APK from the Releases page (coming soon). You can also install the website from Chrome with Install app.
	•	Website: https://sreevachan.github.io/attendance-tracker/ (works on any phone or computer; no widget)

Project structure

	•	docs/: the website, published with GitHub Pages
	•	ios/: the Xcode project for the native iPhone app (private distribution)
	•	version.json: the update notice that apps check

Firestore rules

rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid} {
      allow read, delete: if request.auth != null && request.auth.uid == uid;
      allow create, update: if request.auth != null && request.auth.uid == uid
        && request.resource.data.v == 1
        && request.resource.data.updatedAt is number
        && (
          (request.resource.data.keys().hasOnly(['v','mode','salt','iters','wrapPass','recSalt','wrapRec','blob','updatedAt'])
            && request.resource.data.mode == 'enc'
            && request.resource.data.blob is string
            && request.resource.data.blob.size() < 900000)
          ||
          (request.resource.data.keys().hasOnly(['v','mode','data','updatedAt'])
            && request.resource.data.mode == 'std'
            && request.resource.data.data is string
            && request.resource.data.data.size() < 900000)
        );
    }
    match /{document=**} {
      allow read, write: if false;
    }
  }
}

Roadmap

	•	Android app (APK) with a home-screen widget
	•	Change passphrase, and Face ID unlock
	•	Offline use on the website
	•	College-specific calculation rules and subject-wise attendance
	•	Photo import of attendance sheets

Version history

	•	v2.0: Google sign-in, optional encrypted or standard backup, settings, welcome screen, update notice, app icon, website
	•	v1.1: widget fix
	•	v1.0: first version: calendar, planner and widget

Made by Sreevachan.


Screenshots

<img width="483" height="307" alt="Screenshot 2026-11-25 at 2 09 07 PM" src="https://github.com/user-attachments/assets/fcc354c0-9e41-4723-8ec1-bec5e490aedb" />
<img width="538" height="841" alt="Screenshot 2026-11-25 at 2 08 44 PM" src="https://github.com/user-attachments/assets/ee4f8e06-ddcd-4437-ac91-3f2c98df5bcf" />
<img width="538" height="591" alt="Screenshot 2026-11-25 at 2 08 27 PM" src="https://github.com/user-attachments/assets/137fb16f-6434-4d18-826a-3b2286aecddb" />
<img width="578" height="591" alt="Screenshot 2026-11-25 at 2 04 56 PM" src="https://github.com/user-attachments/assets/af1ff576-9b3f-4876-a0f1-6c1be84bfcac" />
<img width="578" height="776" alt="Screenshot 2026-11-25 at 2 04 41 PM" src="https://github.com/user-attachments/assets/cbd05c10-b4ef-4d72-9137-388472875c9c" />
<img width="1289" height="2509" alt="IMG_2809" src="https://github.com/user-attachments/assets/dcd1ef5f-ac86-458f-98fb-23944dd82582" />
