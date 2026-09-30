# UniVibe Android (native, Kotlin + Jetpack Compose)

Native Android client for the UniVibe / CampusConnect Flask website.

## 1. Backend: add the mobile API (one file + two lines)

1. Copy `backend/routes/mobile_api.py` into your Flask project's `routes/` folder.
2. In `app.py`, add next to the other blueprint imports and registrations:

   ```python
   from routes.mobile_api import mobile_bp, init_mobile_auth
   ...
   app.register_blueprint(mobile_bp)
   init_mobile_auth(app)
   ```
3. Make sure `SECRET_KEY` is set as a **fixed environment variable** in production (Vercel).
   The app's login tokens are signed with it; if it changes, everyone is signed out.

How it works: the app logs in at `POST /api/v1/auth/login` and receives a signed token (30 days).
It sends `Authorization: Bearer <token>`; a `before_request` hook turns that into the same session
values the website uses, so all your existing `/api/*` JSON endpoints (discover, action, messages,
wall likes/comments, profile update, block/report) work for the app unchanged. `mobile_api.py` only
adds the endpoints the website served as HTML pages (me, matches, network, conversations,
notifications, public profile, wall feed).

## 2. Run locally

```
python app.py            # serves on 127.0.0.1:5001
```

- **Emulator:** works out of the box (`http://10.0.2.2:5001/`).
- **Real phone:** change the last line of `app.py` to `host='0.0.0.0'`, find your PC's LAN IP,
  and add `univibe.baseUrl=http://<PC-IP>:5001/` to `gradle.properties`.

## 3. Build the app

1. Open this folder in **Android Studio** (Ladybug or newer) and let Gradle sync
   (Android Studio creates the Gradle wrapper files if they are missing).
2. Run on an emulator/device. Debug builds use `BASE_URL` from `gradle.properties`;
   release builds use `https://univibe-gilt.vercel.app/` (edit `releaseBaseUrl` in `app/build.gradle.kts`).

## What's in the app

| Screen | Data source |
|---|---|
| Sign in | `POST /api/v1/auth/login` |
| Discover (Friends / Networking / Dating, my campus / nearby) | `GET /api/discover`, `POST /api/action` |
| Connections + member directory | `GET /api/v1/matches`, `/api/v1/network` |
| Chats (polling every 3 s while open) | `/api/v1/conversations…`, `POST /api/messages/send` |
| Campus Wall (filters, like, comments, new post) | `/api/v1/wall`, `/api/campus-wall/*` |
| Notifications | `/api/v1/notifications` |
| My profile (edit name/course/year/bio) and public profiles (block/report) | `/api/v1/me`, `/api/profile/update`, `/api/safety/*` |

## Not in this first version

- Sign-up, OTP and student verification, onboarding, photo upload and privacy settings
  (the sign-in screen tells users to finish those on the website first).
- Sending photos/voice notes in chat (they display, but sending is text only).
- Real-time chat over Socket.IO (polling is used; it also works on Vercel serverless).
- Collaboration hub, polls/events, push notifications, admin panel.

## Note on testing

The Flask endpoints were tested against your bundled database. The Android code has **not been
compiled** in this environment, so expect to fix a few small compile errors on first build.

## Getting an APK without installing Android Studio

Push this folder to a GitHub repo, open **Actions -> Build APK -> Run workflow**, and download
`app-debug.apk` from the finished run's artifacts (about 5 minutes). It is pointed at
`https://univibe-gilt.vercel.app/`, so deploy `mobile_api.py` to Vercel first, otherwise login will fail.
Allow "install unknown apps" on your phone to install it.
