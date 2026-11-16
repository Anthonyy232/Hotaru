// node store/demo/library.cjs <dir> : an invented Jellyfin library for the store screenshots, in <dir>/Movies and <dir>/Shows.
// Every title, plot and image is made up; the posters reuse readme/build.cjs's invented titles and artwork.
// Each video is its backdrop panning slowly, with a quiet tone, so it plays like a real file.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { launch } = require('../../lib.cjs');

const dir = process.argv[2];
if (!dir) throw new Error('usage: node store/demo/library.cjs <dir>');

// Scenes are drawn on a 1600x900 canvas with the subject in the middle third, so a 2:3 poster is its centre crop.
// `v` (0 to 1) varies the light for episode stills.
const grad = (id, a, b) => `<defs><linearGradient id="${id}" x2="0" y2="1"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient></defs>`;
const sky = (a, b) => `${grad('s', a, b)}<rect width="1600" height="900" fill="url(#s)"/>`;
const hill = (fill, y, amp, shift = 0) => `<path fill="${fill}" d="M0 ${y} C${400 + shift} ${y - amp} ${800 + shift} ${y + amp} ${1200 + shift} ${y} S1550 ${y - amp} 1600 ${y - amp / 2} V900 H0z"/>`;
const stars = n => Array.from({ length: n }, (_, i) => `<circle cx="${(i * 389) % 1600}" cy="${(i * 211) % 520}" r="${1.5 + (i % 3)}" fill="#E8EEFF" opacity="${0.4 + (i % 4) / 6}"/>`).join('');
const scenes = {
  field: v => `${sky('#4A1812', '#7C2C1E')}<circle cx="${880 - v * 120}" cy="${260 + v * 60}" r="110" fill="#FFC56A"/>
    ${hill('#C2442A', 600, 90)}${hill('#200C08', 740, 70, 120)}`,
  perihelion: v => `${sky('#27386A', '#6E91D9')}${stars(40)}<circle cx="800" cy="440" r="${150 + v * 20}" fill="#E4ECFF"/>
    <ellipse cx="800" cy="440" rx="300" ry="84" transform="rotate(-18 800 440)" fill="none" stroke="#6F8FE0" stroke-width="18"/><circle cx="1060" cy="318" r="26" fill="#E4ECFF"/>`,
  copper: v => `${sky('#25313A', '#41606D')}<circle cx="${980 - v * 200}" cy="150" r="46" fill="#C9D7DD"/>
    ${[[380, 330, '#4E6A78'], [610, 260, '#4E6A78'], [900, 200, '#4E6A78'], [1150, 300, '#4E6A78'], [300, 470, '#10161C'], [560, 540, '#10161C'], [760, 420, '#10161C'], [1010, 500, '#10161C'], [1240, 440, '#10161C']]
    .map(([x, y, c], i) => `<rect x="${x}" y="${y}" width="${110 + (i % 3) * 30}" height="${900 - y}" fill="${c}"/>`).join('')}`,
  tide: v => `<rect width="1600" height="900" fill="#0E3A52"/>
    <path fill="#1E6F8F" d="M0 ${270 + v * 40} C400 180 900 380 1600 240 V480 C900 600 400 420 0 510z"/>
    <path fill="#2B88A8" d="M0 600 C450 510 1000 690 1600 570 V780 C1000 900 450 720 0 810z"/>`,
  rings: v => `${sky('#2E1B42', '#4B2D66')}${stars(24)}${[[330, '#211432'], [270, '#7B4FA3'], [210, '#261735'], [145, '#E8B4D8'], [55, '#1A0F24']]
    .map(([r, c]) => `<circle cx="800" cy="${560 + v * 30}" r="${r}" fill="${c}"/>`).join('')}`,
  pines: v => `${sky('#20402A', '#38623B')}<circle cx="${640 + v * 160}" cy="170" r="46" fill="#E2D47C"/>
    ${[[880, 160, 300], [600, 330, 180], [1080, 380, 170], [400, 460, 130], [1260, 470, 140]].map(([x, top, half]) =>
      `<path fill="#5B8B4A" d="M${x} ${top} ${x + half / 2} 900 H${x - half / 2}z"/>`).join('')}${hill('#183020', 800, 30)}`,
  dune: v => `${sky('#5A3A1C', '#8E5E2E')}<circle cx="${860 - v * 100}" cy="${360 + v * 40}" r="130" fill="#FBE4BC"/>
    ${hill('#B07A3E', 620, 110)}${hill('#6E4520', 760, 80, -150)}`,
  harbor: v => `${sky('#1D2B52', '#3F5E9C')}<rect y="560" width="1600" height="340" fill="#14203F"/>
    ${Array.from({ length: 11 }, (_, i) => `<circle cx="${380 + i * 85}" cy="${540 - (i % 3) * 14}" r="9" fill="#FFD48A"/>
      <rect x="${376 + i * 85}" y="${575 + v * 20}" width="8" height="${80 + (i % 4) * 30}" rx="4" fill="#FFD48A" opacity="0.35"/>`).join('')}
    <path fill="#0B1430" d="M560 560 h480 l-60 70 h-360z"/><rect x="760" y="380" width="14" height="180" fill="#0B1430"/>`,
  lantern: v => `${grad('lt', '#3A2252', '#6E2E58')}<rect width="1600" height="900" fill="url(#lt)"/>
    <circle cx="${1000 - v * 260}" cy="${330 + v * 40}" r="250" fill="#F2AA62"/>
    ${hill('#A0456A', 640, 70)}${hill('#4A2436', 770, 50, 100)}
    ${Array.from({ length: 7 }, (_, i) => `<circle cx="${420 + i * 130 + v * 60}" cy="${500 - (i % 3) * 50}" r="5" fill="#FFF0B0"/>`).join('')}`,
  sea: v => `<rect width="1600" height="900" fill="#0F3049"/><circle cx="${700 + v * 300}" cy="200" r="120" fill="#9EDFD2"/>
    ${['#1D6582', '#123D57', '#1D6582', '#0C2A40', '#1D6582'].map((c, i) => hill(c, 400 + i * 110, 40 * (i % 2 ? 1 : -1), v * 200)).join('')}`,
};

const movies = [
  ['A Field in August', 2019, 'field', 'PG-13', 7.4, ['Drama'], 'A harvest crew of strangers spends one long summer on a farm that is about to be sold, and each of them decides what to leave behind.'],
  ['Perihelion', 2023, 'perihelion', 'PG-13', 8.2, ['Science Fiction', 'Drama'], 'Six months from the sun and three from home, the last engineer on a survey ship has to decide whether to wake the crew.'],
  ['Copper Street', 2021, 'copper', 'R', 7.1, ['Crime', 'Thriller'], 'A night-shift cab driver picks up the same silent passenger three nights running, and starts to recognise the route.'],
  ['Tidewater', 2018, 'tide', 'PG', 6.9, ['Drama', 'Family'], 'Two sisters return to their late father\'s boatyard to sell it, and find his unfinished boat waiting for the spring tide.'],
  ['Orbits', 2020, 'rings', 'PG', 7.8, ['Animation', 'Science Fiction'], 'A small repair robot on a ring station sets out to find where the music in the old maintenance tunnels comes from.'],
  ['Pine Hollow', 2022, 'pines', 'PG-13', 7.0, ['Mystery'], 'When the fire lookout stops answering the radio, a park ranger climbs the tower and finds the log written a week ahead.'],
  ['Dry Season', 2017, 'dune', 'PG-13', 6.6, ['Adventure'], 'A caravan guide and a cartographer cross a desert that moves faster than their maps.'],
  ['Harbor Lights', 2024, 'harbor', 'PG', 7.6, ['Romance', 'Comedy'], 'A ferry captain and a lighthouse keeper argue over the radio every night for a year before either of them thinks to meet.'],
];
const shows = [
  ['Lantern Season', 2025, 'lantern', 'TV-PG', 8.1, ['Animation', 'Drama', 'Fantasy'], 'In a river town that lights a thousand paper lanterns every autumn, a girl apprentices herself to the old ferryman.', [
    ['The Ferryman', 'Mio arrives in the river town with one suitcase and a letter nobody wants to read.'],
    ['Paper and Glue', 'The lantern makers\' guild refuses to teach an outsider, so Mio learns by watching through the window.'],
    ['High Water', 'The river rises early and the ferry has to cross at night.'],
    ['Upstream', 'The river festival is two nights away and the paper boats still will not float. Mio follows the old ferryman upstream, where the fireflies gather, to learn what her grandmother never wrote down.'],
    ['A Thousand Lights', 'The festival begins, and the whole town waits to see if Mio\'s lantern will rise.'],
    ['After the Festival', 'Morning on the river, and a letter finally gets read.'],
  ]],
  ['Saltmarsh', 2024, 'sea', 'TV-14', 7.7, ['Drama', 'Mystery'], 'A marine biologist takes a winter post on a tidal island cut off from the mainland twice a day.', [
    ['Low Tide', 'Nell crosses the causeway for the last time before the winter storms.'],
    ['The Census', 'Counting seals turns up one more than there should be.'],
    ['Spring Tide', 'The highest tide in twenty years reaches the station door.'],
    ['Causeway', 'Someone has been crossing at night, and the footprints only go one way.'],
  ]],
];

// Lantern Season S01E04, the item resumed on Home, has chapters and an SRT track for the player shots.
const chapters = [[0, 'Opening'], [70, 'The Crossing'], [175, 'Upstream'], [290, 'Night Market'], [400, 'Lanterns']];
const subtitles = [
  [12, 'The fireflies only come out where the water is still.'],
  [20, 'Then we wait for it to be still.'],
  [34, 'Hold the lantern lower. Let them come to you.'],
  [46, 'My grandmother used to say they remember the way home.'],
];

const nfoEscape = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');
const tags = obj => Object.entries(obj).flatMap(([k, v]) => [].concat(v).map(x => `  <${k}>${nfoEscape(x)}</${k}>`)).join('\n');
const nfo = (root, fields) => `<?xml version="1.0" encoding="utf-8" standalone="yes"?>\n<${root}>\n${tags(fields)}\n</${root}>\n`;
const clock = s => `${String(Math.floor(s / 3600)).padStart(2, '0')}:${String(Math.floor(s / 60) % 60).padStart(2, '0')}:${String(s % 60).padStart(2, '0')},000`;

// The backdrop, a little larger than 1080p, panned with a slow sine; chapters from an ffmetadata file.
function video(backdrop, out, seconds, withChapters) {
  if (fs.existsSync(out)) return;
  const args = ['-y', '-loglevel', 'error', '-loop', '1', '-framerate', '24', '-i', backdrop,
    '-f', 'lavfi', '-i', `sine=frequency=196:sample_rate=48000:duration=${seconds}`];
  if (withChapters) {
    const meta = `${out}.ffmeta`;
    fs.writeFileSync(meta, ';FFMETADATA1\n' + chapters.filter(([t]) => t < seconds).map(([t, title], i, all) =>
      `[CHAPTER]\nTIMEBASE=1/1\nSTART=${t}\nEND=${all[i + 1] ? all[i + 1][0] : seconds}\ntitle=${title}\n`).join(''));
    args.push('-i', meta, '-map_chapters', '2');
  }
  args.push('-filter_complex', "[0:v]crop=1920:1080:'96+96*sin(t/23)':'54+54*cos(t/31)',format=yuv420p[v];[1:a]volume=0.05[a]",
    '-map', '[v]', '-map', '[a]', '-t', String(seconds), '-c:v', 'libx264', '-preset', 'veryfast', '-tune', 'stillimage', '-crf', '26',
    '-c:a', 'aac', '-b:a', '96k', out);
  execFileSync('ffmpeg', args, { stdio: 'inherit' });
  if (withChapters) fs.rmSync(`${out}.ffmeta`);
}

const srt = () => subtitles.map(([t, line], i) => `${i + 1}\n${clock(t)} --> ${clock(t + 6)}\n${line}\n`).join('\n');

(async () => {
  const { shoot, close } = await launch();
  const image = (scene, v, width, height, out) => shoot(
    `<style>body { margin: 0 } svg { display: block }</style><svg width="${width}" height="${height}" viewBox="0 0 1600 900" preserveAspectRatio="xMidYMid slice">${scenes[scene](v)}</svg>`,
    { width, height, out });
  const art = async (folder, scene) => {
    await image(scene, 0, 600, 900, path.join(folder, 'poster.png'));
    await image(scene, 0, 1920, 1080, path.join(folder, 'backdrop.png'));
  };
  // <base>.mkv, and <base>.en.srt beside the item with chapters.
  const clip = async (scene, v, base, seconds, special) => {
    const pan = `${base}-pan.png`;
    await image(scene, v, 2112, 1188, pan);
    video(pan, `${base}.mkv`, seconds, special);
    fs.rmSync(pan);
    if (special) fs.writeFileSync(`${base}.en.srt`, srt());
  };

  for (const [title, year, scene, mpaa, rating, genres, plot] of movies) {
    const folder = path.join(dir, 'Movies', `${title} (${year})`);
    fs.mkdirSync(folder, { recursive: true });
    await art(folder, scene);
    fs.writeFileSync(path.join(folder, 'movie.nfo'), nfo('movie', { title, year, plot, mpaa, rating, genre: genres }));
    await clip(scene, 0, path.join(folder, `${title} (${year})`), 360 + (year % 5) * 30, false);
  }

  for (const [title, year, scene, mpaa, rating, genres, plot, episodes] of shows) {
    const folder = path.join(dir, 'Shows', title);
    const season = path.join(folder, 'Season 01');
    fs.mkdirSync(season, { recursive: true });
    await art(folder, scene);
    fs.writeFileSync(path.join(folder, 'tvshow.nfo'), nfo('tvshow', { title, year, plot, mpaa, rating, genre: genres }));
    for (const [i, [name, episodePlot]] of episodes.entries()) {
      const base = path.join(season, `${title} S01E${String(i + 1).padStart(2, '0')}`);
      const v = i / (episodes.length - 1);
      await image(scene, v, 1280, 720, `${base}-thumb.png`);
      await clip(scene, v, base, 240 + i * 20, title === 'Lantern Season' && i === 3);
      fs.writeFileSync(`${base}.nfo`, nfo('episodedetails', { title: name, season: 1, episode: i + 1, plot: episodePlot, aired: `${year}-10-${String(3 + i * 7).padStart(2, '0')}` }));
    }
  }
  await close();
})();
