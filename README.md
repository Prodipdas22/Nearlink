# NearLink Phase 2 — direct nearby messaging

This update adds an Android-native Google Nearby Connections transport to the existing WebView chat.

## Implemented in this build
- Nearby advertising and discovery using `P2P_CLUSTER`.
- Device discovery events delivered to the existing web UI.
- Connection request with a verification-code confirmation dialog. Users must compare the code on both phones before accepting.
- Text payload transfer over Nearby Connections.
- Delivery acknowledgements at the app protocol level.
- Local IndexedDB conversation storage remains in place.
- Nearby Connections provides encrypted, reliable, full-duplex transport. The app adds visual authentication through the shared verification digits.

## Important scope notes
- This is an initial Phase 2 integration, not a security-audited production messenger.
- Pairing is currently a per-session user-confirmed connection; persistent trusted-device identity and key pinning are not yet implemented.
- The message UI associates incoming messages with the currently selected conversation (or creates a generic Nearby conversation). A device-specific conversation mapping is a follow-up.
- Android Nearby Connections can use Bluetooth and Wi-Fi radios. It is not a Bluetooth-only transport selector.
- Both phones need Google Play services with Nearby Connections support. Turn Bluetooth and Wi-Fi on, grant Nearby/Bluetooth permissions, and keep NearLink open on both devices.
- Delivery means the receiving app processed the packet and sent an ACK; it does not mean the recipient read the message.
- No mesh forwarding, background service, offline queue/retry policy, or persistent cryptographic identity is included yet.

## Build
Push the repository changes to `main`. GitHub Actions runs `:app:assembleDebug` and uploads `NearLink-debug-apk`.

## Two-phone test
1. Install the same new APK on both Android phones.
2. Enable Bluetooth and Wi-Fi on both phones. Internet is not required for the peer-to-peer session, but Google Play services must be present.
3. Open NearLink on both phones and grant the requested permissions.
4. Tap **Nearby** on both phones. Both devices advertise and discover.
5. When the connection prompt appears, compare the six-digit verification code on both screens and accept only if they match.
6. Send a text from one phone. Confirm the receiver gets it and the sender's status changes to `delivered` after the ACK returns.

If discovery does not start, check that Google Play services is enabled and updated, Bluetooth/Wi-Fi are on, and all requested permissions are granted.
