# Security

## Reporting a vulnerability

Please report security issues privately through GitHub's
[security advisories](https://github.com/SanniniciStyle/WatchSync/security/advisories/new)
rather than in a public issue.

## The one-time watch setup over wireless debugging

WatchSync grants the watch app a few permissions that Wear OS only grants through debugging. It
does so once, from the phone, over the watch's wireless debugging, using
[libadb-android](https://github.com/MuntashirAkon/libadb-android) (also used by App Manager). Then
it turns wireless debugging off.

What this means for security:

- **Pairing** uses the six-digit code shown on the watch (SPAKE2, as in Android's own `adb pair`):
  a device on the network that doesn't know the code can't pair or sit in the middle.
- **The connection** after pairing is TLS. As in Android's own adb, the watch authenticates the
  phone's key; the phone doesn't check the watch's certificate (adb certificates are self-signed,
  there is no authority to check them against). WatchSync only connects to the address that has
  just completed pairing, and sends nothing secret over it: only the commands that grant its own
  permissions.
- **The phone's adb identity** (an RSA key and certificate) is created on the phone and kept in
  storage excluded from backups. It never leaves the phone.
- **Wireless debugging is switched off** at the end of the setup, whether it succeeded or not.

### Static analysis of libadb-android

libadb-android had never been through a security audit, so we ran GitHub CodeQL (extended
security suite) on it (October 2026). It reported four findings, all inherent to the adb protocol
rather than flaws:

| Finding | Where | Why it is expected |
|---|---|---|
| Unsafe certificate trust (×2) | `AdbConnection`, `PairingConnectionCtx` | adb's TLS certificates are self-signed; pairing is authenticated by SPAKE2 and the code, the connection by the client key — same as Android's adb |
| Trust manager accepting any certificate | `SslUtils` | same as above |
| RSA without OAEP | `AndroidPubkey` | that code signs adb's authentication token (PKCS#1, as the protocol requires); it doesn't encrypt anything |

A manual review focused on WatchSync's threat model (a device on the home network posing as the
watch during setup) found the pairing sound — the SPAKE2 secret is bound to the TLS session as in
Android's adb, and fails closed — and no leaks of keys or of the code. It found robustness issues:
no limit on the packet size a peer may announce (an out-of-memory crash), no timeouts (a hang),
unbounded buffering. WatchSync guards against what it can from its side: every adb call runs
under a time limit that drops the connection when it expires, and errors thrown back by the
library, out-of-memory ones included, end the setup with an error. An oversized packet handled on
the library's own reader thread can still crash the app; that needs a fix in the library (a cap on
the announced packet size, as Android's adb has), which we plan to propose upstream.

We also fixed a stream-handling bug in it while building WatchSync (upstream pull request
[#35](https://github.com/MuntashirAkon/libadb-android/pull/35)).
