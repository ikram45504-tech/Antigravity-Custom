# Security Policy

## Reporting a vulnerability

Please **do not** open a public issue for security problems.

Use GitHub's private reporting flow: **Security → Advisories → Report a vulnerability** on this
repository. If that is unavailable, contact the maintainer via the Telegram group listed in
`README.md` and ask for a private channel.

Please include: affected version (`versionName` in `app/build.gradle.kts`), device/Android version,
reproduction steps, and impact. We aim to acknowledge within 7 days.

## Supported versions

Only the latest published release receives security fixes.

## Threat model

This app is unusual: it bundles a Linux userspace, executes downloaded binaries, and exposes a
localhost HTTP API. The areas below are treated as security boundaries.

### OAuth credentials

- Google OAuth 2.0 with PKCE (S256) and a random `state` on every authorization request.
- The loopback callback binds `127.0.0.1` only, and callbacks with a mismatched `state` are rejected.
- Token files (`antigravity-oauth-token`, `jetski-standalone-oauth-token`, account store) are
  written **owner-only**. Refresh tokens are long-lived credentials.
- The `client_secret` in `OAuthManager` belongs to an installed-app client. Per Google's model it is
  not treated as confidential, but it should not be used to build a separate service.

### Local gateway (`127.0.0.1:45161`)

`SystemGatewayServer` exposes toast, open-URL, clipboard, wakelock and system-info endpoints.
Mitigations: loopback-only bind, 64 KiB body cap, 8 KiB request-head cap, and clipboard reads
require `POST`. It has **no authentication**: any process running as this UID (and, on a rooted
device, any root process) can call it. Do not treat it as an isolation boundary.

### Downloaded binaries

The core engine is fetched from a remote manifest. `sha512` from that manifest is verified before
the binary is executed, and an ELF magic-byte check is performed. The bootstrap archive is pinned to
a hardcoded SHA-256. **If you publish a manifest, keep the checksum in sync** — a mismatch now
aborts the update rather than proceeding.

### Shell interpolation

Paths, `git status` filenames and catalogue entries are interpolated into `bash -c` strings.
`EngineExecutor.shellQuote()` and `CustomizationTracker.sanitizeRepoUrl()`/`sanitizeSubPath()` exist
to keep that safe. **Any new shell command built from external data must use them**, or pass
`workingDir` instead of `cd`.

### Signing

Release signing keys are never committed. `assembleRelease` refuses to build without a real signing
config. If a keystore was ever committed, treat it as compromised and rotate it — a leaked release
key lets an attacker ship an APK that Android accepts as a legitimate upgrade.
