# Working on Hotaru

A Jellyfin app for Meta Quest 3 and 3S: Kotlin, Compose, Media3 and the Meta Spatial SDK. Gradle needs JDK 21 in `JAVA_HOME` and `sdk.dir` in `local.properties`.

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

- Core modules use no Android, Compose, Spatial SDK or Jellyfin SDK types, and `check` enforces the module edges. Logic goes in the lowest layer that can own it; errors cross layers as values, not exceptions.
- The UI looks like visionOS: glass with one warm accent, defined once in the shared tokens. Build with Meta's Spatial UI set and keep its control sizes and hit targets.
- A green build doesn't prove the scene works, and there is no emulator: UI and playback changes can only be verified on a headset, with hands and controllers.
- Never commit a server address, credentials or signing material.

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

- Bump commit `chore: release vX.Y.Z`; tag `vX.Y.Z`, which must match the app's `versionName`.
- Title `Hotaru X.Y.Z`.
- Notes: banner image first, then `### New`, `### Improved`, `### Fixed` (only those that apply). One line per change, written for users; no internals, dependency lists or test counts. End with the install link(s) and the full changelog link.
- Banner: on the orphan `media` branch, `node releases/banner.cjs <checkout> <version> "<highlight>"` writes `releases/v<version>.png`; reference its raw URL.
- Generated images (`.github/readme/`, `site/img/`, the banners) are built from the `media` branch; its README has the steps.
- Licensed MPL-2.0 because the app links Meta's proprietary Spatial SDK, which no GPL code can ship beside: never add a GPL dependency (Jellyfin's `media3-ffmpeg-decoder` was removed for this).
- Pushing the tag makes CI build the signed APK and attach it to the draft release, which must already exist. Raise `versionCode` for every release: CI fails the tag if it isn't higher than the previous tag's. `targetSdk` stays at the level Meta's manifest rules allow.

## Agents

- Pushing, amending and force-pushing are allowed. Ask before publishing a release or submitting to a store.
- Leave changes you didn't make alone. Clean up scratch files, processes and emulators.
- Say what was checked and how, and what wasn't.
- Short replies; one recommendation, not a list of options.
