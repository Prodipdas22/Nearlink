# NearLink MVP 1.0

A mobile-first, installable PWA starter for the NearLink offline communication project.

## What this version does
- Responsive chat interface for mobile and desktop
- Light and dark themes
- Local conversation and message storage with IndexedDB
- Service worker caching for the app shell
- PWA manifest and installable app experience

## Important limitation
This is an interface and local-storage prototype. It does **not** yet send messages to another phone. Bluetooth discovery, Wi-Fi Direct, end-to-end encryption, local gateway and mesh forwarding are future native networking phases. Messages entered here are saved only in this browser profile.

## Run locally
Serve this folder using a local static web server. Opening `index.html` directly with `file://` will not enable service worker/PWA features.

## Publish with GitHub Pages
1. Create a public GitHub repository named `NearLink`.
2. Upload the contents of this folder to the repository root (not the parent folder).
3. Open repository **Settings → Pages**.
4. Under **Build and deployment**, select **Deploy from a branch**.
5. Select branch `main`, folder `/ (root)`, then Save.
6. Wait for the Pages deployment and open the URL shown in Settings → Pages.

## Install on Android
1. Open the HTTPS GitHub Pages URL in Chrome.
2. Allow the first load to finish while online so the service worker can cache the app shell.
3. Open Chrome menu → **Install app** or **Add to Home screen**.
4. Launch NearLink from the home screen.
5. Test the cached interface by enabling airplane mode and reopening it.

## Android phone-only editing workflow
- Download and extract the ZIP.
- Use an Android code editor to edit `index.html`, `styles.css`, or `app.js`.
- Upload changed files to the GitHub repository, keeping the same folder structure.
- Refresh the PWA after deployment. If old assets remain cached, close/reopen the app or clear the site data during development.

## Suggested next engineering milestones
1. Native Android app shell (Kotlin + Jetpack Compose).
2. Android local persistence and device identity.
3. BLE peer discovery and authenticated pairing.
4. Direct encrypted message transport.
5. Android local gateway for browsers on the same local network.
6. Store-and-forward mesh routing.

## Security note
Do not use this prototype for sensitive communications. It has no end-to-end encryption or peer authentication yet.
