# Working on Hotaru

Conventions for anyone changing this codebase, human or AI.

## Targets

This is a new app with nothing released. Write for these floors only:

- Quest 3 / 3S, Horizon OS v69+ (hybrid apps: a panel window plus an immersive activity), Android
  minSdk 34, arm64
- Jellyfin server 12.0+
- Current Kotlin, Compose, Media3, Room, Metro and Spatial SDK APIs; JDK 21

No compatibility code for anything below them: no SDK version checks, compat shims, legacy
endpoints, auth or formats, fallbacks for other devices, or deprecated APIs where a current one
exists. Other platforms are speculative: keep the core platform-neutral, but build no hosts, targets
or setup for them.

## Architecture

- **Respect the layering.** `core/*` has no Android, Compose, Spatial SDK or Jellyfin SDK imports,
  and SDK types stay in their adapter. `dependencyLint` enforces the module edges.
- **Put logic in the lowest layer that can own it.** Rules in `core/domain`, workflows in
  `core/application` use cases, screen state in `core/presentation` view models. `ui`, `platform`
  and `app` stay thin.
- **Ports** (`core/application/.../ports`) only where a layer boundary needs one.
- **Use cases** return `Outcome<T>` with typed `AppError`s; don't throw across layers. Every
  Jellyfin call goes through a use case, because it owns the session and 401 handling. Don't add
  use cases that only forward to an app-scoped port; the view model calls the port.
- **Metro DI** with constructor injection. Each adapter contributes its own binding; session-bound
  objects come from factories.

## Code

- **Simple and direct.** Solve today's problem; no speculative options, abstractions or extension
  points.
- **Stay in scope.** Build what was asked; suggest extras instead of adding them.
- **Not defensive.** Handle what really happens: network errors, unreachable servers, 401s,
  cancellation, optional fields real servers omit. No null checks, `?:` defaults, `else` branches
  on exhaustive `when`s or `try`/`catch` for things that can't happen; let programmer errors fail
  loudly.
- **No fallbacks or workarounds** unless a real platform or server quirk needs one; then say why in
  a comment.
- **Don't hand-roll** what the SDKs, a dependency or this repo already provide. Search first.
- **Delete dead code** you find or create: unused members, params always passed the same value,
  unreachable branches, unused resources, imports and dependencies.
- **Match the surrounding code**: naming, structure, comment density, idioms.
- **Fix it everywhere.** After fixing a bug or a convention slip, look for the same pattern
  elsewhere.
- **UI looks like visionOS**: glass, white labels, bold titles, capsules and concentric corners, from
  `ui/kit`'s tokens. Build with Meta's Spatial UI set, which the kit re-themes, and `ui/kit` for
  what it lacks; keep the set's control sizes and hit targets, never fixed window widths.

## Comments

- Only for a non-obvious **why**: a Jellyfin or Horizon OS quirk, a constraint, a surprising
  decision. One tight line where possible.
- No restating code, section banners, history, review notes or references to old decisions.
- KDoc only when it adds what the signature doesn't: units, ranges, what `null` means, ordering.

## Tests

- Test behaviour that can break: session and auth, playback reporting, tracks, caching, window vs
  immersive rules, device profiles.
- No trivial, obvious or duplicate tests; one meaningful scenario beats several that restate each
  other.
- Build classes with the shared fakes, not the DI graph, and use virtual time, not sleeps.

## Done means

- You have **reviewed your own diff against this file**: over-engineering, over-abstraction,
  defensive code, hand-rolled or duplicated code, low-signal tests, restating comments.
- `./gradlew check :app:quest:assembleDebug` passes with **zero warnings** and **all tests green**.
- Spatial, playback and UI changes are **checked on a headset**, in the window and in immersive and
  switching between them, with hands and controllers (scripts in `tools/`). A green build doesn't
  prove the scene works.
- Behaviour changes are deliberate and called out.

## Dependencies and data

- For a major version bump, read the release notes and check each breaking change against how
  Hotaru uses the library.
- The database migrates destructively until 1.0. When an entity or a stored JSON shape (`MediaItem`,
  `Settings`) changes, bump the version in `Database.kt` and commit the new exported schema in place
  of the old one.

## Git

- **Commit messages are short and informal**: a lowercase summary of the outcome with no prefix or
  trailing punctuation, e.g. `pause when the headset comes off`; bullets under a blank line only
  when they help.
- **No co-authors or AI attribution** in commits, branches or PRs.
- One logical change per commit; squash fix-ups into the commit they fix before pushing.
- Don't commit generated output, local config (`local.properties`) or secrets.

## For AI agents

- Ask before anything hard to undo or visible to others: pushing (approval to commit isn't approval
  to push), opening PRs, deleting branches, rewriting published history.
- Leave changes you didn't make alone, and clean up scratch files and background processes when
  done.
- Report honestly: what was verified and how, what still needs a headset, and anything you changed
  on the headset or machine.
- Keep replies short; give one clear recommendation rather than a list of options.
