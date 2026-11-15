# Hotaru media

Release banners for [Hotaru](https://github.com/Anthonyy232/Hotaru), kept off `main` so it stays light.

The script needs Node and Playwright with Chromium: `npm install playwright` in any folder and point `NODE_PATH` at its `node_modules`. From a Hotaru checkout:

```bash
git worktree add build/media media
node build/media/banner.cjs 0.1.0 "One short highlight"
```

That records the highlight in `banners.json` and writes `releases/v0.1.0.png` (1280x640). Without arguments it redraws every banner. Commit, push, and use the raw URL in the release notes:

`https://raw.githubusercontent.com/Anthonyy232/Hotaru/media/releases/v0.1.0.png`
