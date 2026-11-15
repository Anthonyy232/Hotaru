// Shared by releases/banner.cjs and readme/build.cjs: local fonts, the logo from a Hotaru checkout, and screenshots.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const font = (family, file, style, weight = 400) => `@font-face { font-family: '${family}'; font-style: ${style}; font-weight: ${weight};
  src: url(data:font/woff2;base64,${fs.readFileSync(path.join(__dirname, 'fonts', file)).toString('base64')}) format('woff2'); }`;

const fonts = [
  font('Instrument Serif', 'InstrumentSerif-Regular.woff2', 'normal'),
  font('Instrument Serif', 'InstrumentSerif-Italic.woff2', 'italic'),
  font('Inter', 'Inter.woff2', 'normal', '100 900'),
].join('\n');

// An SVG from the checkout's branding/, inline and unsized.
function logo(checkout, name) {
  const file = path.join(checkout, 'branding', name);
  if (!fs.existsSync(file)) throw new Error(`${file} not found: pass the path of a Hotaru checkout`);
  return fs.readFileSync(file, 'utf8')
    .replace(/<title.*?<\/title>/, '')
    .replace(/ width="\d+" height="\d+" role="img" aria-labelledby="title"/, '');
}

async function launch() {
  const browser = await chromium.launch();
  // Writes `out` (PNG), or returns the PNG bytes when `out` is omitted.
  const shoot = async (html, { width, height, scale = 1, clip, out }) => {
    const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: scale });
    await page.setContent(`<!doctype html><meta charset="utf-8"><style>${fonts}</style>${html}`);
    for (const f of ["400 20px 'Instrument Serif'", "italic 400 20px 'Instrument Serif'", "700 20px Inter"]) {
      if (!(await page.evaluate(f => document.fonts.load(f), f)).length) throw new Error(`${f} did not load`);
    }
    await page.evaluate(() => document.fonts.ready);
    const png = await page.screenshot({ path: out, clip, omitBackground: true });
    await page.close();
    return png;
  };
  // PNG bytes to a WebP file of width x height, encoded by Chromium. Clips are whole CSS pixels, so the PNG may be a
  // device pixel larger: the extra comes off the right and the top.
  const webp = async (png, out, { width, height, quality }) => {
    const page = await browser.newPage();
    const data = await page.evaluate(async ({ src, width, height, quality }) => {
      const img = new Image();
      img.src = src;
      await img.decode();
      const canvas = Object.assign(document.createElement('canvas'), { width, height });
      canvas.getContext('2d').drawImage(img, 0, height - img.height);
      return canvas.toDataURL('image/webp', quality).split(',')[1];
    }, { src: 'data:image/png;base64,' + png.toString('base64'), width, height, quality });
    fs.writeFileSync(out, Buffer.from(data, 'base64'));
    await page.close();
  };
  return { shoot, webp, close: () => browser.close() };
}

module.exports = { logo, launch };
