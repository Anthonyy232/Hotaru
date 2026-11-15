// node banner.cjs [<version> "<highlight>"] : 1280x640 release banners in releases/
const { chromium } = require('playwright');
const fs = require('fs');
const dir = __dirname;
const file = `${dir}/banners.json`;
const banners = JSON.parse(fs.readFileSync(file, 'utf8'));
const [version, highlight] = process.argv.slice(2);
if (version) {
  banners[version] = highlight;
  fs.writeFileSync(file, JSON.stringify(banners, null, 2) + '\n');
}
const mark = fs.readFileSync(`${dir}/logo.svg`, 'utf8')
  .replace(/<title.*?<\/title>/, '')
  .replace(' width="256" height="256" role="img" aria-labelledby="title"', '');

const html = (v, text) => `<!doctype html><meta charset="utf-8">
<link href="https://fonts.googleapis.com/css2?family=Instrument+Serif:ital@0;1&display=swap" rel="stylesheet">
<style>
  * { box-sizing: border-box; }
  body { margin: 0; width: 1280px; height: 640px; overflow: hidden; display: grid; grid-template-columns: 1fr 470px; background: #17110E; color: #FFF6E8; font-family: system-ui, sans-serif; }
  .copy { padding: 64px 40px 60px 80px; display: flex; flex-direction: column; }
  .brand { display: flex; align-items: center; gap: 10px; font-size: 26px; font-weight: 600; letter-spacing: -0.01em; }
  .brand svg { width: 36px; height: 36px; }
  .num { margin-top: auto; font: 500 18px ui-monospace, 'Cascadia Mono', monospace; color: #C2B4A3; }
  h1 { margin: 14px 0 0; font: 400 96px/1 'Instrument Serif', Georgia, serif; letter-spacing: -0.01em; text-wrap: balance; }
  h1 i { color: #FFBE7A; }
  .stage { margin: 40px 40px 40px 0; border-radius: 24px; display: grid; place-items: center;
    background: radial-gradient(70% 60% at 18% 0%, #7A4418 0%, transparent 70%), radial-gradient(45% 40% at 60% 76%, rgb(255 190 122 / 0.3) 0%, transparent 70%), #2A1C14; }
  .stage svg { width: 250px; height: 250px; }
</style>
<div class="copy">
  <div class="brand">${mark}hotaru</div>
  <div class="num">Release ${v}</div>
  <h1>${text}</h1>
</div>
<div class="stage">${mark}</div>`;

// The last word carries the accent, as the README headlines do with one word.
const accent = text => text.replace(/(\S+)$/, '<i>$1</i>');

(async () => {
  const browser = await chromium.launch();
  for (const [v, text] of version ? [[version, highlight]] : Object.entries(banners)) {
    const page = await browser.newPage({ viewport: { width: 1280, height: 640 } });
    await page.setContent(html(v, accent(text)), { waitUntil: 'networkidle' });
    await page.evaluate(() => document.fonts.ready);
    if (!await page.evaluate(() => document.fonts.check("96px 'Instrument Serif'"))) throw new Error('Instrument Serif did not load');
    await page.screenshot({ path: `${dir}/releases/v${v}.png` });
    await page.close();
  }
  await browser.close();
})();
