# Hotaru media

Generated images for [Hotaru](https://github.com/Anthonyy232/Hotaru), kept off `main` so it stays light: the release banners, the README panels, the site images, the social preview and the store art.

| Path | What |
| --- | --- |
| `lib.cjs` | Shared by both scripts: the bundled fonts, the logo read from a Hotaru checkout, screenshots. |
| `releases/banner.cjs`, `banners.json` | Release banners, `releases/v<version>.png`. |
| `readme/build.cjs` | Every README, site and social image, written into a Hotaru checkout. |
| `store/build.cjs` | The Meta Horizon Store cover art, icon and logo, `store/*.png`. |
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

That records the highlight in `releases/banners.json` and writes `releases/v0.1.0.png` (1280x640). With only the checkout path it redraws every banner. Commit, push, and use the raw URL in the release notes:

`https://raw.githubusercontent.com/Anthonyy232/Hotaru/media/releases/v0.1.0.png`

## Store art

```bash
node store/build.cjs ../..
```

That writes the five covers, `icon.png` (512x512) and the transparent `logo.png` into `store/`, at the sizes in Meta's [asset guidelines](https://developers.meta.com/horizon/resources/asset-guidelines/). The five store screenshots are not built here: Meta requires real captures from the headset.

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
