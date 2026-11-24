# Contributing

Thanks for helping improve Antigravity for Android.

## Getting started

See the **Building from source** and **Tests** sections of `README.md`. In short:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug
./gradlew testDebugUnitTest
python3 -m unittest discover -s app/src/test/python -v
```

The debug build needs no signing key. Release builds fail closed without one; see `SECURITY.md`.

## Pull requests

- Keep changes focused; one concern per PR.
- Add or update a test for any change to pure logic (version comparison, path/URL handling, cron
  parsing, transcript parsing, JSON mapping). Those are the highest-value places for tests because
  they are cheap to run and easy to get subtly wrong.
- Run `./gradlew testDebugUnitTest` before opening a PR; CI runs it on every push.
- Do not commit keystores, `local.properties`, tokens, or build output — `.gitignore` covers these.

## Code conventions

- Kotlin, 4-space indent, `camelCase` for functions/properties, `PascalCase` for types.
- Follow the existing delegate structure under `viewmodel/`: the view model holds state, delegates
  hold one concern each. Avoid growing `AntigravityViewModel` or `SettingsScreen` further — new UI
  belongs in a component or a new screen file.
- All user-facing strings go in `res/values/strings.xml` **and** `res/values-id/strings.xml`. Do not
  hardcode display text in Kotlin.
- Prefer returning `Result`/nullable values over throwing across coroutine boundaries.

## Security-sensitive areas

Read `SECURITY.md` before touching any of:

- `oauth/` — credential handling and the authorization flow
- `gateway/` — the loopback HTTP API
- `install/` — downloaded and executed binaries, archive extraction
- `executor/EngineExecutor` — anything interpolated into a shell command

When building a shell command from external data, use `EngineExecutor.shellQuote()`, pass
`workingDir` instead of `cd '...'`, and validate repo URLs/sub-paths with
`CustomizationTracker.sanitizeRepoUrl()` / `sanitizeSubPath()`.

## Overlay scripts (`app/src/main/assets/`)

- `overlay/bin/agy-daemon` and `pkg/pkg` are Python/shell hybrid entry points; keep the shebang shim
  intact.
- Shell scripts must pass `bash -n`; Python must pass `py_compile` (both checked in CI).
- Pure helpers (e.g. `lib/daemon/scheduler.py`) should stay importable without Android so they can be
  unit-tested from `app/src/test/python/`.

## Reporting bugs

Include the app version, Android version and device, and steps to reproduce. Logs can be exported
from Settings → Logs. For security issues, follow `SECURITY.md` instead of opening a public issue.
