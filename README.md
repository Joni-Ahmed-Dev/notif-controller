# Notification Controller (v1.5)

Android app that turns a chosen app's notifications ON/OFF. It uses a
NotificationListenerService: when an app is blocked, its notifications are
cancelled the moment they arrive. Settings still shows the target app's
switch as ON; the notifications are effectively hidden.

Behaviour (unchanged since v1.3, design from v1.4): each row has only a
select checkbox; the master button acts only on ticked apps; newly added
apps start ticked; long-press a row to remove it; the home screen widget
is a small 48dp logo button (target app icon + green/red dot).

Instant push (new in v1.5): when Muse changes the blocked list, the
Cloudflare Worker sends a Firebase Cloud Messaging data message to the
`notif-controller` topic and the phone applies it in seconds. The
15-minute poll stays as the backup.

## Build

Built on GitHub Actions (`.github/workflows/android.yml`). Gradle does not
run in the local sandbox, so there is no local build for v1.5.

Secrets the workflow needs (repo Settings > Secrets and variables > Actions):
- `GOOGLE_SERVICES_JSON` — contents of google-services.json for the
  Firebase project (project deft-processor-338922, app
  com.joni.notifcontroller). The file itself is never committed.
- `NOTIF_READ_TOKEN` — the Worker READ_TOKEN, recovered from the v1.4 APK's
  BuildConfig (or freshly rotated with
  `bin/deploy_notif_controller.py set-secret` before the run; rotating
  breaks the old app's sync until v1.5 is installed).
- `NOTIF_DEBUG_KEYSTORE_BASE64` — base64 of `~/.android/notif-debug.keystore`
  (alias `notif`, password `android`), so v1.5 installs over v1.4.

The Worker also holds the `FCM_SERVICE_ACCOUNT_JSON` secret (the Firebase
service account JSON, set with
`bin/deploy_notif_controller.py set-fcm-secret`) and a `PUSH_TOKEN` secret
for Muse's `bin/notif.py` push calls. Neither file is in this repo.
