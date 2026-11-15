# Working on Hotaru

A Jellyfin app for Meta Quest 3 and 3S: Kotlin, Compose, Media3 and the Meta Spatial SDK, as a panel window plus an immersive activity. Gradle needs JDK 21 in `JAVA_HOME` and `sdk.dir` in `local.properties`.

## Code

- Simple and direct. No speculative abstractions, options or extension points.
- Stay in scope; suggest extras instead of adding them.
- Use what the platform, framework, dependencies and this repo already provide before writing a helper.
- Validate only at real boundaries (user input, storage, network, other processes). No checks, fallbacks or try/catch for cases that can't happen.
- One source of truth for each value or rule.
- Delete dead code you find or create.
- Match the surrounding code.
- Fix root causes, and fix the same pattern everywhere it occurs.

## Comments

- Only where the reason isn't clear from the code: a quirk, a race, a non-obvious decision.
- Short fragments, not sentences. Never restate code, narrate, or label sections.
- Delete comments that no longer match.

## Tests

- Behaviour and the edges that can break. No trivial, duplicate, obvious or low-signal tests.
- A regression test fails without its fix.
- Deterministic: fake time and observable events, no sleeps.
- A failing test is a bug until shown otherwise.

## Hotaru

- Floors: Horizon OS v69, minSdk 34, arm64, Jellyfin 12.0. Nothing is released, so no compatibility code below them: no SDK version checks, shims, legacy endpoints or deprecated APIs.
- `core/*` imports no Android, Compose, Spatial SDK or Jellyfin SDK types; `dependencyLint` (part of `check`) enforces the module edges. Logic goes in the lowest layer that can own it.
- Use cases return `Outcome<T>`; nothing throws across layers. Every Jellyfin call goes through a use case, which owns the session and 401 handling.
- The UI looks like visionOS: glass, white labels, bold titles, capsules, concentric corners. Colours, sizes and shapes come from the tokens in `ui/kit/Theme.kt`; build with Meta's Spatial UI set, which the kit re-themes, and keep its control sizes and hit targets. No fixed window widths.
- A green build doesn't prove the scene works, and there is no emulator. Scene, playback and UI changes need a headset: the window, the cinema and switching between them, with hands and controllers.
- With the headset on USB in developer mode, `tools/quest-deploy.ps1` installs and launches the debug build (optionally signed in) and records logcat and screenshots to `build/quest-run`; `tools/quest-dev.ps1` taps, swipes and captures through the debug-only bridge while nobody wears it.
- The database migrates destructively until 1.0. When an entity or a stored JSON shape (`MediaItem`, `Settings`) changes, bump the version in `Database.kt` and replace the exported schema.
- `LiveServerIntegrationTest` runs only with `HOTARU_DEV_SERVER`, `HOTARU_USER` and `HOTARU_PASSWORD` set. Never commit a server address or credentials.

## Done means

- Diff reviewed against this file.
- `./gradlew check :app:quest:assembleDebug` passes with zero warnings.
- UI changes seen in the running app on a headset. Docs and metadata changes need no end-to-end run.
- README updated for anything users will notice; behaviour changes called out.

## Docs

- README is for users: what it does, how to get it, how to use it. No internals, no agent instructions.
- This file is for agents: only what can't be found quickly in the code.
- No meta docs in the repo (notes, audits, plans, reports).

## Git

- `type: summary` or `type(scope): summary`: lowercase, imperative, no full stop, about 70 characters at most. Types: `feat` `fix` `refactor` `perf` `test` `docs` `style` `build` `ci` `chore`.
- Body only to say why. `Fixes #123` closes an issue.
- One logical change per commit. Squash fix-ups before merging.
- Branch off `main`. A single commit fast-forwards; several merge with `merge: <summary>`.
- No AI attribution in commits, branches, PRs or releases.
- One maintainer: amending and force-pushing to tidy history is fine. Keep contributors as authors. Delete stale branches.

## Pull requests

- Title in commit format. Body: what changed, why, how it was checked. Short.

## Releases

- None yet. The first will be: bump commit `chore: release vX.Y.Z`, tag `vX.Y.Z`, title `Hotaru X.Y.Z`.
- Notes: banner image first, then `### New`, `### Improved`, `### Fixed` (only those that apply). One line per change, written for users; no internals, dependency lists or test counts. End with the install link(s) and the full changelog link.
- Before the first APK: `media3-ffmpeg-decoder` is GPL-3.0 and can't ship in one APK with Meta's proprietary Spatial SDK. Drop it or get Jellyfin's permission first.
- A release build is signed from `hotaru.keystore*` and `hotaru.key*` in `~/.gradle/gradle.properties`, never from the repo.

## Agents

- Pushing, amending and force-pushing are allowed. Ask before publishing a release or submitting to a store.
- Leave changes you didn't make alone. Clean up scratch files, processes and emulators.
- Say what was checked and how, and what wasn't.
- Short replies; one recommendation, not a list of options.
