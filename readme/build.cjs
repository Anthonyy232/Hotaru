// node readme/build.cjs <checkout> : README panels, site images and the social preview, written into a Hotaru checkout.
// No headset screenshots: each scene redraws the app in HTML from ui/kit's tokens (Theme.kt, Glass.kt) and the screens'
// layout, in dp, scaled into its stage. Titles and artwork are invented.
const fs = require('fs');
const path = require('path');
const { logo, launch } = require('../lib.cjs');

const checkout = process.argv[2];
if (!checkout) throw new Error('usage: node readme/build.cjs <checkout>');
const wordmark = dark => logo(checkout, dark ? 'hotaru-wordmark.svg' : 'hotaru-wordmark-on-light.svg')
  .replace('viewBox="0 0 698 256"', 'viewBox="20 62 658 148"'); // cropped to the letters, as ui/kit's Wordmark is

// Page tokens, from site/style.css.
const page = dark => dark
  ? { bg: '#17110E', ink: '#FFF6E8', muted: '#B8AA9A', accent: '#FFBE7A',
    wash: 'radial-gradient(60% 70% at 12% 0%, #7A4418 0%, transparent 75%), radial-gradient(45% 55% at 100% 8%, #5A3418 0%, transparent 70%), radial-gradient(40% 40% at 50% 100%, #8A521F 0%, transparent 75%), #2A1C14' }
  : { bg: '#FFF6E8', ink: '#1B1512', muted: '#6A5B4E', accent: '#CF6A12',
    wash: 'radial-gradient(60% 70% at 12% 0%, #FFDDAA 0%, transparent 75%), radial-gradient(45% 55% at 100% 8%, #F2A257 0%, transparent 70%), radial-gradient(40% 40% at 50% 100%, #EC9A4E 0%, transparent 75%), #F4CC99' };

const icon = (d, fill) => `<svg class="i" viewBox="0 0 24 24" ${fill ? 'fill="currentColor"' : 'fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"'}>${d}</svg>`;
const I = {
  home: icon('<path d="M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z"/>', true),
  search: icon('<circle cx="10.5" cy="10.5" r="6"/><path d="m15 15 5 5"/>'),
  movies: icon('<rect x="3" y="4.5" width="18" height="12.5" rx="2.5"/><path d="m10.2 8.3 4.3 2.45-4.3 2.45z" fill="currentColor"/><path d="M8.5 20.5h7"/>'),
  shows: icon('<rect x="3.5" y="7.5" width="17" height="12" rx="2.5"/><path d="m8.5 3.5 3.5 4 3.5-4"/>'),
  folder: icon('<path d="M3 6.5A1.5 1.5 0 0 1 4.5 5h4.6l2 2.2h8.4A1.5 1.5 0 0 1 21 8.7v9.8a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5z"/>', true),
  settings: icon('<circle cx="12" cy="12" r="3.6"/><path d="M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7"/>'),
  sort: icon('<path d="M8 19V5M4.5 8.5 8 5l3.5 3.5M16 5v14M12.5 15.5 16 19l3.5-3.5"/>'),
  heart: icon('<path d="M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.4 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z"/>', true),
  play: icon('<path d="M7 4.5v15a.8.8 0 0 0 1.2.7l12-7.5a.8.8 0 0 0 0-1.4l-12-7.5A.8.8 0 0 0 7 4.5z"/>', true),
  pause: icon('<rect x="6" y="4.5" width="4" height="15" rx="1.2"/><rect x="14" y="4.5" width="4" height="15" rx="1.2"/>', true),
  back10: icon('<path d="M5.5 9A7.5 7.5 0 1 1 4.5 13"/><path d="M5 4.5V9h4.5"/><text x="12.4" y="15.6" font-size="7.5" font-weight="700" text-anchor="middle" fill="currentColor" stroke="none" font-family="Inter">10</text>'),
  forward10: icon('<path d="M18.5 9A7.5 7.5 0 1 0 19.5 13"/><path d="M19 4.5V9h-4.5"/><text x="11.6" y="15.6" font-size="7.5" font-weight="700" text-anchor="middle" fill="currentColor" stroke="none" font-family="Inter">10</text>'),
  captions: icon('<rect x="3" y="5.5" width="18" height="13" rx="3"/><path d="M10.5 10.2a2.2 2.2 0 1 0 0 3.6M17 10.2a2.2 2.2 0 1 0 0 3.6"/>'),
  environment: icon('<path d="m3 19 6-8 4 5 2.5-3L21 19z"/><circle cx="16.5" cy="7" r="2"/>'),
  immersive: icon('<path d="M3 8.5C3 7 4 6 5.5 6h13C20 6 21 7 21 8.5v7c0 1.5-1 2.5-2.5 2.5h-3.2L12 15l-3.3 3H5.5C4 18 3 17 3 15.5z"/>'),
  window: icon('<rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="m10.2 9.3 4.3 2.7-4.3 2.7z" fill="currentColor"/>'),
  chevron: icon('<path d="M14.5 5.5 8 12l6.5 6.5"/>'),
  check: icon('<path d="m4.5 12.5 5 5 10-11"/>'),
  next: icon('<path d="M5 5.5v13a.7.7 0 0 0 1.1.6l9.4-6.5a.7.7 0 0 0 0-1.2L6.1 4.9A.7.7 0 0 0 5 5.5zM18 5v14"/>'),
  recenter: icon('<circle cx="12" cy="12" r="2.2" fill="currentColor"/><path d="M12 3v5M12 16v5M3 12h5M16 12h5"/>'),
};

// Invented artwork: posters 200x300, stills 1600x900.
const svg = (w, h, body) => `<svg class="art" viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMidYMid slice">${body}</svg>`;
const grad = (id, a, b) => `<defs><linearGradient id="${id}" x2="0" y2="1"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient></defs>`;
const waves = (y, amp, colors) => colors.map((c, i) => {
  const top = y + i * 110, phase = i % 2 ? 1 : -1;
  return `<path fill="${c}" d="M0 ${top} C400 ${top - amp * phase} 800 ${top + amp * phase} 1200 ${top} S1600 ${top - amp * phase} 1700 ${top} V900 H0z"/>`;
}).join('');
const art = {
  lantern: svg(1600, 528, `${grad('lt', '#3A2252', '#6E2E58')}<rect width="1600" height="528" fill="url(#lt)"/>
    <circle cx="1085" cy="205" r="256" fill="#F2AA62"/>
    <path fill="#A0456A" d="M0 400 C300 340 700 340 1000 380 S1450 440 1600 330 V528 H0z"/>
    <path fill="#4A2436" d="M0 470 C400 430 800 420 1200 445 S1500 465 1600 455 V528 H0z"/>`),
  sea: svg(1600, 900, `<rect width="1600" height="900" fill="#0F3049"/><circle cx="420" cy="170" r="140" fill="#9EDFD2"/>
    ${waves(300, 40, ['#1D6582', '#123D57', '#1D6582', '#0C2A40', '#1D6582'])}`),
  field: svg(200, 300, `${grad('fa', '#4A1812', '#7C2C1E')}<rect width="200" height="300" fill="url(#fa)"/><circle cx="133" cy="100" r="31" fill="#FFC56A"/>
    <path fill="#C2442A" d="M0 190 C40 160 80 170 110 200 S170 230 200 170 V300 H0z"/><path fill="#200C08" d="M0 230 C50 200 110 205 200 280 V300 H0z"/>`),
  perihelion: svg(200, 300, `${grad('pe', '#27386A', '#6E91D9')}<rect width="200" height="300" fill="url(#pe)"/>
    <circle cx="40" cy="60" r="2.5" fill="#E8EEFF"/><circle cx="60" cy="230" r="2" fill="#E8EEFF"/><circle cx="100" cy="135" r="40" fill="#E4ECFF"/>
    <ellipse cx="100" cy="135" rx="78" ry="22" transform="rotate(-18 100 135)" fill="none" stroke="#6F8FE0" stroke-width="5"/><circle cx="168" cy="102" r="7" fill="#E4ECFF"/>`),
  copper: svg(200, 300, `${grad('cs', '#25313A', '#41606D')}<rect width="200" height="300" fill="url(#cs)"/><circle cx="150" cy="48" r="14" fill="#C9D7DD"/>
    <rect x="58" y="110" width="40" height="190" fill="#4E6A78"/><rect x="134" y="92" width="34" height="208" fill="#4E6A78"/>
    <rect x="12" y="148" width="30" height="152" fill="#10161C"/><rect x="104" y="166" width="30" height="134" fill="#10161C"/><rect x="168" y="140" width="32" height="160" fill="#10161C"/>`),
  tide: svg(200, 300, `<rect width="200" height="300" fill="#0E3A52"/><path fill="#1E6F8F" d="M0 90 C60 60 120 120 200 80 V160 C120 200 60 140 0 170z"/>
    <path fill="#2B88A8" d="M0 200 C70 170 130 230 200 190 V260 C130 300 70 240 0 270z"/>`),
  rings: svg(200, 300, `${grad('ri', '#2E1B42', '#4B2D66')}<rect width="200" height="300" fill="url(#ri)"/>
    <circle cx="100" cy="200" r="96" fill="#211432"/><circle cx="100" cy="200" r="78" fill="#7B4FA3"/><circle cx="100" cy="200" r="60" fill="#261735"/>
    <circle cx="100" cy="200" r="42" fill="#E8B4D8"/><circle cx="100" cy="200" r="16" fill="#1A0F24"/>`),
  pines: svg(200, 300, `${grad('pi', '#20402A', '#38623B')}<rect width="200" height="300" fill="url(#pi)"/><circle cx="44" cy="50" r="14" fill="#E2D47C"/>
    <path fill="#5B8B4A" d="M120 50 160 300H80zM50 100 76 300H24z"/>`),
  dune: svg(200, 300, `${grad('du', '#5A3A1C', '#8E5E2E')}<rect width="200" height="300" fill="url(#du)"/><circle cx="132" cy="110" r="38" fill="#FBE4BC"/>`),
  harbor: svg(200, 300, `${grad('ha', '#1D2B52', '#3F5E9C')}<rect width="200" height="300" fill="url(#ha)"/>`),
};

const app = `<style>
  .app, .app * { box-sizing: border-box; }
  .app { position: absolute; font: 500 14px/20px Inter, sans-serif; color: #FFF; }
  .app .i { width: 24px; height: 24px; flex: none; }
  .art { position: absolute; inset: 0; width: 100%; height: 100%; }
  .row { display: flex; align-items: center; }
  /* Glass.kt: tint and lit rim */
  .glass { position: relative; background: linear-gradient(#585450D6, #423E3AD6); }
  .glass::after { content: ''; position: absolute; inset: 0; border-radius: inherit; padding: 1px; pointer-events: none;
    background: linear-gradient(135deg, #FFFFFF66, #FFFFFF0F 35%, #FFFFFF0F 75%, #FFFFFF2E);
    -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0); mask-composite: exclude; }
  .lift { box-shadow: 0 30px 70px -20px #0009; }
  .window { border-radius: 40px; overflow: hidden; }
  .pane { border-radius: 32px; }
  .t-display { font: 700 40px/46px Inter; letter-spacing: -0.02em; }
  .t-h1 { font: 700 26px/32px Inter; letter-spacing: -0.02em; }
  .t-h3 { font: 700 16px/22px Inter; }
  .t-b1s { font: 700 14px/20px Inter; }
  .t-b2 { font: 500 12px/16px Inter; }
  .t-b2s { font: 700 12px/16px Inter; }
  .dim { color: #FFFFFF99; }
  .btn { display: inline-flex; align-items: center; gap: 8px; height: 48px; padding: 0 24px; border-radius: 24px; white-space: nowrap; background: #FFFFFF1A; }
  .btn.on { background: #FFF; color: #000; }
  .circle { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 50%; background: #FFFFFF1A; flex: none; }
  .circle.on { background: #FFF; color: #000; }
  .tag { border: 1px solid #FFFFFF99; border-radius: 6px; padding: 2px 8px; color: #FFFFFF99; font: 700 12px/16px Inter; }
  .choice { display: flex; padding: 4px; border-radius: 24px; background: #00000040; }
  .choice div { flex: 1; display: grid; place-items: center; height: 40px; border-radius: 20px; color: #FFFFFF99; }
  .choice .on { background: #FFFFFF3D; color: #FFF; font-weight: 700; }
</style>`;

// AppContent.kt's tab bar: 48 dp tabs in a capsule, beside the window.
const tabBar = selected => `<div class="glass lift" style="padding: 8px; border-radius: 32px; display: grid; gap: 4px">
  ${['home', 'search', 'movies', 'shows', 'folder', 'settings'].map(t =>
    `<div class="circle" style="background: ${t === selected ? '#FFFFFF3D' : 'transparent'}">${I[t]}</div>`).join('')}
</div>`;

// The window beside its tab bar, laid out in dp; `zoom` scales it into the stage.
const shell = ({ x, y, zoom, width, height, selected }, body) => `<div style="position: absolute; left: ${x}px; top: ${y}px"><div class="app row" style="position: relative; zoom: ${zoom}; height: ${height}px">
  <div style="width: 96px; padding-left: 16px">${tabBar(selected)}</div>
  <div class="glass window lift" style="width: ${width}px; height: ${height}px">${body}</div>
</div></div>`;

// HomeScreen.kt: the hero, its backdrop fading into the glass.
const home = shell({ x: 37, y: 40, zoom: 0.86, width: 1020, height: 560, selected: 'home' }, `
  <div style="position: relative; height: 336px">
    <div style="position: absolute; inset: 0; -webkit-mask: linear-gradient(#000 50%, transparent)">
      ${art.lantern}
      <div style="position: absolute; inset: 0; background: linear-gradient(#0000004D, #00000099)"></div>
      <div style="position: absolute; inset: 0; width: 70%; background: linear-gradient(90deg, #00000099, transparent)"></div>
    </div>
    <div style="position: absolute; left: 32px; bottom: 32px; display: grid; gap: 12px; justify-items: start">
      <div class="t-display">Lantern Season</div>
      <div class="row t-b2 dim" style="gap: 12px"><span class="tag">S01E04</span><span>24m</span><span>★ 8.1</span><span>Animation · Drama · Fantasy</span></div>
      <div style="max-width: 720px; text-align: left">The river festival is two nights away and the paper boats still will not float. Mio follows the old ferryman upstream, where the fireflies gather, to learn what her grandmother never wrote down.</div>
      <div class="row" style="gap: 16px; margin-top: 8px"><span class="btn on">${I.play}Resume</span><span class="btn">Details</span></div>
    </div>
  </div>`);

// LibraryViewScreen.kt: the bar over a grid of 196 dp posters.
const poster = ([name, year, image, favorite]) => `<div style="display: grid; gap: 8px; width: 196px">
  <div style="position: relative; height: 294px; border-radius: 16px; overflow: hidden">${art[image]}
    ${favorite ? `<div class="glass" style="position: absolute; top: 8px; right: 8px; width: 28px; height: 28px; border-radius: 50%; display: grid; place-items: center; color: #FF453A">${I.heart.replace('class="i"', 'style="width: 18px; height: 18px"')}</div>` : ''}
  </div>
  <div><div>${name}</div><div class="t-b2 dim">${year}</div></div>
</div>`;
const browse = shell({ x: 27, y: 64, zoom: 0.75, width: 920, height: 580, selected: 'movies' }, `
  <div class="row" style="height: 72px; padding: 0 32px; gap: 16px">
    <div class="t-h1" style="flex: 1">Movies</div>
    <span class="btn">${I.sort}By name</span><span class="btn on">Unplayed</span><span class="btn">Favorites</span>
  </div>
  <div style="display: grid; grid-template-columns: repeat(4, 196px); gap: 24px 16px; padding: 8px 32px">
    ${[['A Field in August', 2019, 'field'], ['Perihelion', 2023, 'perihelion', true], ['Copper Street', 2021, 'copper'], ['Tidewater', 2018, 'tide'],
    ['Orbits', 2020, 'rings'], ['Pine Hollow', 2022, 'pines', true], ['Dry Season', 2017, 'dune'], ['Harbor Lights', 2024, 'harbor']].map(poster).join('')}
  </div>`);

// PlayerScreens.kt's control bar; `roomy` labels the hand-off button.
const seekBar = `<div class="row" style="gap: 12px">
  <div class="t-b1s" style="min-width: 64px">14:52</div>
  <div style="flex: 1; height: 60px; position: relative">
    <div style="position: absolute; left: 12px; right: 12px; top: 27px; height: 6px; border-radius: 3px; overflow: hidden; background: #FFFFFF3D">
      <div style="position: absolute; inset: 0 29% 0 0; background: #FFFFFF3D"></div>
      <div style="position: absolute; inset: 0 38% 0 0; background: #FFBE7A"></div>
      ${[7, 34, 71, 93].map(f => `<div style="position: absolute; left: calc(${f}% - 1px); width: 2px; height: 6px; background: #00000099"></div>`).join('')}
    </div>
  </div>
  <div class="t-b1s dim" style="min-width: 64px; text-align: right">-9:08</div>
</div>`;
const controlBar = ({ roomy }) => `<div class="glass pane" style="padding: 16px 24px; display: grid; gap: 4px">
  <div class="row" style="gap: 16px"><div class="circle">${I.chevron}</div>
    <div><div class="t-h3">Lantern Season</div><div class="t-b2 dim">S01E04 · The Ferryman</div></div></div>
  ${seekBar}
  <div class="row" style="gap: 16px">
    <div class="circle on">${I.pause}</div><div class="circle">${I.back10}</div><div class="circle">${I.forward10}</div>
    <div style="flex: 1"></div>
    ${roomy
    ? `<div class="circle on">${I.captions}</div><span class="btn">${I.immersive}Watch in immersive</span>`
    : `<div class="circle">${I.captions}</div><div class="circle on">${I.environment}</div><div class="circle">${I.window}</div>`}
  </div>
</div>`;

const track = (label, on) => `<div class="row" style="gap: 12px; min-height: 48px; padding: 0 12px">
  <span style="color: ${on ? '#FFBE7A' : 'transparent'}; display: flex">${I.check}</span>${label}</div>`;
const trackPicker = `<div class="glass pane" style="width: 760px; padding: 12px">
  <div class="row" style="gap: 12px; align-items: start">
    <div style="flex: 1; display: grid; gap: 8px"><div class="t-b1s dim" style="padding: 0 12px">Audio</div>
      <div style="display: grid; gap: 4px">${track('Japanese - Opus - Stereo - Default', true)}${track('English - AAC - 5.1')}</div></div>
    <div style="flex: 1; display: grid; gap: 8px"><div class="t-b1s dim" style="padding: 0 12px">Subtitles</div>
      <div style="display: grid; gap: 4px">${track('Off')}${track('English - Default - ASS', true)}${track('English (Signs) - ASS')}</div></div>
  </div>
  <div style="padding: 12px 16px; display: grid; gap: 8px">Speed
    <div class="choice">${['0.5×', '0.75×', '1×', '1.25×', '1.5×', '2×'].map(s => `<div class="${s === '1×' ? 'on' : ''}">${s}</div>`).join('')}</div></div>
  <div class="row" style="padding: 8px 16px; gap: 16px"><div style="flex: 1">Subtitle delay</div>
    <span class="btn">Earlier</span><div class="t-b1s dim" style="width: 88px; text-align: center">+0.25 s</div><span class="btn">Later</span></div>
</div>`;

// WindowPlayer: the video under a column of skip button, track picker and bar, 24 dp in from the window's edges.
const player = `<div style="position: absolute; left: 30px; top: 51px"><div class="app window lift" style="position: relative; zoom: 0.575; width: 960px; height: 700px; background: #000">
  ${art.sea}
  <div style="position: absolute; left: 24px; right: 24px; bottom: 24px; display: grid; gap: 12px; justify-items: end">
    <span class="btn on">${I.next}Skip intro</span>
    ${trackPicker}
    <div style="justify-self: stretch">${controlBar({ roomy: true })}</div>
  </div>
</div></div>`;

// The immersive cinema: the screen, and CinemaControls (640 dp wide) just under it.
const cinema = `<div style="position: absolute; inset: 0; background: #0C0A09">
  <div style="position: absolute; left: 82px; top: 16px; width: 448px; height: 252px; overflow: hidden; box-shadow: 0 0 70px #2C7A9466">${art.sea}
    <div style="position: absolute; left: 0; right: 0; bottom: 34px; text-align: center; font: 700 13px/17px Inter; color: #FFF; text-shadow: 0 1px 3px #000C">The fireflies only come out<br>when the lanterns go dark.</div>
  </div>
  <div style="position: absolute; left: 146px; top: 270px"><div class="app" style="position: relative; zoom: 0.5; width: 640px; padding: 12px; display: grid; gap: 12px">
    ${controlBar({ roomy: false })}
    <div class="glass pane" style="padding: 12px; display: grid; gap: 8px">
      <div class="row" style="padding: 4px 0 0 16px"><div class="t-h3" style="flex: 1">Cinema</div><span class="btn">${I.recenter}Recenter</span></div>
      <div class="row" style="padding: 0 16px; gap: 16px; height: 48px"><div style="width: 140px">Screen size</div>
        <div style="flex: 1; height: 32px; border-radius: 16px; background: #00000040; position: relative">
          <div style="position: absolute; inset: 4px; right: auto; width: 45%; border-radius: 12px; background: #FFF"></div></div>
        <div class="t-b1s dim" style="width: 64px; text-align: right">6.0 m</div></div>
      <div style="padding: 12px 16px; display: grid; gap: 8px">Surroundings
        <div class="choice"><div>Passthrough</div><div class="on">Dimmed</div><div>Dark</div></div></div>
    </div>
  </div></div>
</div>`;

const base = dark => {
  const t = page(dark);
  return `<style>
  * { box-sizing: border-box; }
  html, body { margin: 0; background: transparent; }
  body { width: 1280px; overflow: hidden; color: ${t.ink}; font-family: system-ui, sans-serif; }
  .card { position: relative; width: 1280px; overflow: hidden; border-radius: 28px; background: ${t.bg}; }
  .serif { font-family: 'Instrument Serif', Georgia, serif; font-weight: 400; letter-spacing: -0.01em; }
  .serif i { color: ${t.accent}; }
  .copy p { color: ${t.muted}; }
  .stage { position: relative; overflow: hidden; background: ${t.wash}; }
</style>${app}`;
};

const hero = (dark, { height, brand, h1, size, stage, radius = 28 }) => `${base(dark)}<style>
  .card { height: ${height}px; text-align: center; border-radius: ${radius}px; }
  .brand { margin-top: ${brand}px; line-height: 0; }
  .brand svg { height: 26px; width: auto; }
  h1 { margin: ${h1}px 0 0; font-size: ${size}px; line-height: 0.97; }
  p { margin: 22px auto 0; max-width: 560px; text-wrap: balance; font-size: 20px; line-height: 1.45; }
  .stage { position: absolute; left: 110px; right: 110px; top: ${stage}px; bottom: 0; border-radius: 24px 24px 0 0; }
</style>
<div class="card copy">
  <div class="brand">${wordmark(dark)}</div>
  <h1 class="serif">your library,<br>on a <i>giant</i> screen</h1>
  <p>A Jellyfin app for Meta Quest 3 and 3S. Browse in a window, then watch in an immersive cinema.</p>
  <div class="stage">${home}</div>
</div>`;

const feature = (dark, n, title, text, stage) => `${base(dark)}<style>
  .card { height: 560px; display: grid; grid-template-columns: 1fr 640px; }
  .copy { padding: 0 56px 0 72px; display: flex; flex-direction: column; justify-content: center; }
  .num { font: 500 15px ui-monospace, 'Cascadia Code', monospace; color: ${page(dark).muted}; }
  h2 { margin: 14px 0 0; font-size: 64px; line-height: 1.02; }
  p { margin: 20px 0 0; font-size: 19px; line-height: 1.5; }
  .stage { margin: 28px 28px 28px 0; border-radius: 20px; }
</style>
<div class="card">
  <div class="copy"><div class="num">${n}</div><h2 class="serif">${title}</h2><p>${text}</p></div>
  <div class="stage">${stage}</div>
</div>`;

const features = [
  ['browse', '01', 'Browse in a <i>window</i>', 'Your libraries sit in a window you can resize: continue watching, next up, latest additions, search, and filters for unplayed and favorites.', browse],
  ['player', '02', 'Every track, <i>in reach</i>', 'Switch audio and subtitles, change speed, nudge subtitle timing and skip intros without leaving the picture. Styled ASS subtitles are drawn by libass, animation included.', player],
  ['cinema', '03', 'A <i>cinema</i> to yourself', 'Go immersive and the video becomes a screen from 1 to 12 metres wide. Keep your room in passthrough, dim it, or turn it off.', cinema],
];

const readme = path.join(checkout, '.github', 'readme');
const siteDir = path.join(checkout, 'site', 'img');
const scale = 1.6;

(async () => {
  const { shoot, webp, close } = await launch();
  // The site shows each image cropped to its stage, at the sizes site/index.html declares.
  const site = async (html, height, [x, y, w, h], out) => webp(
    await shoot(html, { width: 1280, height, scale, clip: { x, y, width: Math.ceil(w / scale), height: Math.ceil(h / scale) } }),
    `${siteDir}/${out}.webp`, { width: w, height: h, quality: 0.9 });
  for (const dark of [false, true]) {
    const theme = dark ? 'dark' : 'light';
    const top = hero(dark, { height: 760, brand: 56, h1: 32, size: 112, stage: 446 });
    await shoot(top, { width: 1280, height: 760, scale, out: `${readme}/hero-${theme}.png` });
    await site(top, 760, [110, 445, 1696, 503], `hero-${theme}`);
    for (const [name, n, title, text, stage] of features) {
      const html = feature(dark, n, title, text, stage);
      await shoot(html, { width: 1280, height: 560, scale, out: `${readme}/feature-${name}-${theme}.png` });
      await site(html, 560, [640, 28, 980, 808], `${name}-${theme}`);
    }
  }
  await shoot(hero(true, { height: 640, brand: 40, h1: 14, size: 100, stage: 368, radius: 0 }), { width: 1280, height: 640, out: `${siteDir}/social.png` });
  await close();
})();
