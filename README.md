# Hotaru media

Generated images for [Hotaru](https://github.com/Anthonyy232/Hotaru), kept off `main` so it stays light: the release banners, the README panels, the site images, the social preview and the store art.

| Path | What |
| --- | --- |
| `lib.cjs` | Shared by both scripts: the bundled fonts, the logo read from a Hotaru checkout, screenshots. |
| `releases/banner.cjs`, `banners.json` | Release banners, `releases/v<version>.png`. |
| `readme/build.cjs` | Every README, site and social image, written into a Hotaru checkout. |
| `store/build.cjs` | The Meta Horizon Store cover art, icon and logo, `store/*.png`. |
| `store/demo/` | An invented Jellyfin library and its server setup, for the store screenshots `store/screenshot-*.png`. |
| `fonts/` | Instrument Serif and Inter, under the SIL Open Font License. Nothing is fetched at build time. |

## Setup

Needs Node. From a Hotaru checkout on `main`:

```bash
git worktree add build/media media
cd build/media
npm ci
npx playwright install chromium
```

Both scripts take the path of the Hotaru checkout as their first argument; they read the logo from its `branding/`.

## Release banner

```bash
node releases/banner.cjs ../.. 0.1.0 "One short highlight"
```

That records the highlight in `releases/banners.json` and writes `releases/v0.1.0.png` (1280x640 at 2x). With only the checkout path it redraws every banner. Commit, push, and use the raw URL in the release notes:

`https://raw.githubusercontent.com/Anthonyy232/Hotaru/media/releases/v0.1.0.png`

## Store art

```bash
node store/build.cjs ../..
```

That writes the five covers, `icon.png` (512x512) and the transparent `logo.png` into `store/`, at the sizes in Meta's [asset guidelines](https://developers.meta.com/horizon/resources/asset-guidelines/).

## Store screenshots

Meta requires real captures from the headset, so `store/screenshot-1.png` to `-5.png` are taken from a debug build signed in to a throwaway Jellyfin server holding an invented library: no real titles or artwork appear. Needs a Quest on USB with developer mode, ffmpeg on `PATH`, and the Windows portable Jellyfin zip at a version the app accepts (repo.jellyfin.org, `server/windows/latest-stable/amd64`), unpacked somewhere disposable.

1. The library, about 90 MB: 8 films and 2 shows with posters, backdrops, episode stills and NFO files; each video is its backdrop panning, with a quiet tone. Lantern Season S01E04 has chapters and an SRT track.

   ```bash
   node store/demo/library.cjs <library>
   ```

2. A server with its own folders and port, so it touches no other install. Before its first start, write `<data>/config/network.xml` with `InternalHttpPort` 8196 and `AutoDiscovery` false. Then:

   ```bash
   <jellyfin>/jellyfin.exe --datadir <data>/data --configdir <data>/config --cachedir <data>/cache --logdir <data>/log --webdir <jellyfin>/jellyfin-web --ffmpeg <jellyfin>/ffmpeg.exe
   node store/demo/server.cjs http://127.0.0.1:8196 <library> Demo <password>
   ```

   That finishes first-run setup, adds the libraries with every internet provider off, scans, and marks items played, part-watched and favourite so Home has Continue Watching and Next Up.

3. From the Hotaru checkout, reach it over USB (no firewall rule needed) and sign in as another account; accounts already on the headset stay saved:

   ```powershell
   adb reverse tcp:8196 tcp:8196
   adb shell setprop debug.oculus.guardian_pause 1   # a headset on a desk otherwise waits behind the boundary prompt
   tools/quest-deploy.ps1 -Server http://127.0.0.1:8196 -User Demo -Password <password>
   ```

4. Capture, tapping with `tools/quest-dev.ps1 tap` in dp (the window is 1024x640 dp, the cinema's controls 640 dp wide). `capture -Scale <n>` redraws the window at n times its 1280x800 pixels, with the video frame under it, into `build/quest-run/window.png`:
   - Home and Perihelion's details at `-Scale 2.25`, the Movies tab scrolled up about 100 dp at `-Scale 2.56`, and Lantern Season S01E04 playing in the window with its controls shown at `-Scale 2`.
   - The cinema: Watch in immersive with the SRT track on, pause on a subtitle, set the screen to about 5.5 m in Cinema settings and `tools/quest-dev.ps1 recenter`, then grow the screen to about 9 m so it fills the eye without moving back. The headset has to sit level and face straight ahead, or the screen comes out skewed. Take the headset's own screenshot, one undistorted eye at 1440x1440, written to `/sdcard/Oculus/Screenshots`:

     ```bash
     adb shell am startservice -n com.oculus.metacam/.capture.CaptureService -a TAKE_SCREENSHOT
     ```

5. Crop to 2560x1440 without adding or scaling anything in the window shots. Meta's review rejects a screenshot with a plain strip along any edge as having "empty borders" (VRC.Quest.Asset.5), so every edge has to cut through artwork or controls: the browse shots are cropped inside the app's panel, leaving out the rail and the rounded corners, the Movies shot runs from inside the first poster column to inside the last, the player is cropped to its 16:9 video, and the cinema eye is cropped to a 16:9 band inside the screen and scaled up. The controls panel sits below the cinema screen and cannot be in the frame.

   ```bash
   ffmpeg -i home.png -vf "crop=2560:1440:295:45,format=rgb24" store/screenshot-1.png      # details the same, as screenshot-3
   ffmpeg -i movies.png -vf "crop=2560:1440:512:282,format=rgb24" store/screenshot-2.png
   ffmpeg -i player.png -vf "crop=2560:1440:0:80,format=rgb24" store/screenshot-4.png
   ffmpeg -i eye.jpg -vf "crop=1296:729:<left>:<top>,scale=2560:1440:flags=lanczos,format=rgb24" store/screenshot-5.png
   ```

6. Afterwards: Settings, Switch user, and pick the headset's usual account; `adb shell setprop debug.oculus.guardian_pause 0`; `adb reverse --remove tcp:8196`; delete the screenshots on the headset; stop the server and delete it and the library.

## README, site and social images

```bash
node readme/build.cjs ../..
```

That writes into the checkout:

- `.github/readme/hero-{light,dark}.png` and `feature-{browse,player,cinema}-{light,dark}.png`, at 1.6x.
- `site/img/{hero,browse,player,cinema}-{light,dark}.webp`, the same images cropped to their stage.
- `site/img/social.png`, 1280x640, which is also uploaded by hand as the repository's social preview.

Commit the changed images on `main`.

There are no screenshots: the app runs only on a headset, so each scene in `readme/build.cjs` redraws it in HTML and CSS from the tokens in `ui/kit` (`Theme.kt`, `Glass.kt`) and the screens' layout in dp, scaled into its stage. The titles and artwork are invented. When the app's look changes, change the scenes to match. The README and site say these images are renders.

Body text uses the system UI font and the step numbers its monospace font, so a build on another OS can differ slightly in those; the headlines and the app's text use the bundled fonts.
