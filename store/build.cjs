// node store/build.cjs <checkout> : the Meta Horizon Store cover art, icon and logo in store/
const { logo, launch } = require('../lib.cjs');

const [checkout] = process.argv.slice(2);
if (!checkout) throw new Error('usage: node store/build.cjs <checkout>');
const wordmark = logo(checkout, 'hotaru-wordmark.svg');
const icon = logo(checkout, 'hotaru-app-icon.svg');

// Meta's sizes. Covers carry the title only, centred well inside the edges: the store crops and overlays them.
const covers = {
  'hero-cover': [3000, 900, 0.3],
  'cover-landscape': [2560, 1440, 0.42],
  'cover-square': [1440, 1440, 0.56],
  'cover-portrait': [1008, 1440, 0.62],
  'mini-landscape': [1080, 360, 0.36],
};

const cover = (w, h, share) => `<style>
  body { margin: 0; width: ${w}px; height: ${h}px; overflow: hidden; display: grid; place-items: center;
    background: radial-gradient(60% 80% at 22% 0%, #7A4418 0%, transparent 70%), radial-gradient(40% 55% at 70% 88%, rgb(255 190 122 / 0.3) 0%, transparent 70%), #2A1C14; }
  svg { width: ${Math.round(w * share)}px; }
</style>${wordmark}`;

const fill = (svg, w, h) => `<style>body { margin: 0; width: ${w}px; height: ${h}px; overflow: hidden; } svg { display: block; width: 100%; height: 100%; }</style>${svg}`;

(async () => {
  const { shoot, close } = await launch();
  for (const [name, [width, height, share]] of Object.entries(covers)) {
    await shoot(cover(width, height, share), { width, height, out: `${__dirname}/${name}.png` });
  }
  await shoot(fill(icon, 512, 512), { width: 512, height: 512, out: `${__dirname}/icon.png` });
  await shoot(fill(wordmark, 2792, 1024), { width: 2792, height: 1024, out: `${__dirname}/logo.png` });
  await close();
})();
