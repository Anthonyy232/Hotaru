// node store/demo/server.cjs <url> <library dir> <user> <password> : sets up a fresh Jellyfin server with the invented
// library from library.cjs. Finishes first-run setup, adds the Movies and Shows libraries with every internet provider off,
// scans, and leaves some items played and some part-watched so Home has Continue Watching and Next Up.
const path = require('path');

const [url, dir, user, password] = process.argv.slice(2);
if (!password) throw new Error('usage: node store/demo/server.cjs <url> <library dir> <user> <password>');

let token = '';
async function api(method, route, body) {
  const auth = `MediaBrowser Client="Hotaru demo", Device="demo", DeviceId="hotaru-demo", Version="1"${token ? `, Token="${token}"` : ''}`;
  const res = await fetch(url + route, { method, headers: { Authorization: auth, 'Content-Type': 'application/json' }, body: body && JSON.stringify(body) });
  if (!res.ok) throw new Error(`${method} ${route}: ${res.status} ${await res.text()}`);
  return res.headers.get('content-type')?.includes('json') ? res.json() : null;
}

// Local NFO and images only: nothing is fetched or saved back.
const libraryOptions = type => ({
  EnableRealtimeMonitor: false, EnableInternetProviders: false, SaveLocalMetadata: false, MetadataSavers: [],
  EnableChapterImageExtraction: false, ExtractChapterImagesDuringLibraryScan: false, EnableTrickplayImageExtraction: false,
  EnableEmbeddedTitles: false, EnableEmbeddedExtrasTitles: false, EnableEmbeddedEpisodeInfos: false, LocalMetadataReaderOrder: ['Nfo'],
  PreferredMetadataLanguage: 'en', MetadataCountryCode: 'US', DisabledSubtitleFetchers: [], SubtitleDownloadLanguages: [],
  TypeOptions: type.map(Type => ({ Type, MetadataFetchers: [], MetadataFetcherOrder: [], ImageFetchers: [], ImageFetcherOrder: [] })),
});

const until = async check => { while (!(await check())) await new Promise(r => setTimeout(r, 2000)); };
const ticks = (item, share) => Math.round(item.RunTimeTicks * share);

(async () => {
  await api('POST', '/Startup/Configuration', { ServerName: 'Demo', UICulture: 'en-US', MetadataCountryCode: 'US', PreferredMetadataLanguage: 'en' });
  await api('GET', '/Startup/User');
  await api('POST', '/Startup/User', { Name: user, Password: password });
  await api('POST', '/Startup/RemoteAccess', { EnableRemoteAccess: true, EnableAutomaticPortMapping: false });
  await api('POST', '/Startup/Complete');
  const auth = await api('POST', '/Users/AuthenticateByName', { Username: user, Pw: password });
  token = auth.AccessToken;
  const userId = auth.User.Id;

  for (const [name, type, types] of [['Movies', 'movies', ['Movie']], ['Shows', 'tvshows', ['Series', 'Season', 'Episode']]]) {
    await api('POST', `/Library/VirtualFolders?name=${name}&collectionType=${type}&refreshLibrary=false&paths=${encodeURIComponent(path.join(dir, name))}`,
      { LibraryOptions: libraryOptions(types) });
  }
  await api('POST', '/Library/Refresh');
  await until(async () => (await api('GET', '/Library/VirtualFolders')).every(f => f.RefreshStatus === 'Idle')
    && (await api('GET', `/Items?Recursive=true&IncludeItemTypes=Episode&userId=${userId}`)).TotalRecordCount === 10);

  const items = (await api('GET', `/Items?Recursive=true&IncludeItemTypes=Movie,Episode&Fields=SeriesName&userId=${userId}`)).Items;
  const find = name => items.find(i => `${i.SeriesName ? `${i.SeriesName} ` : ''}${i.Name}` === name) ?? (() => { throw new Error(`${name} not found`); })();
  // Oldest first, so the last one is the most recently watched and leads Home.
  const watched = [['Lantern Season The Ferryman', 1], ['Lantern Season Paper and Glue', 1], ['Saltmarsh Low Tide', 1], ['Harbor Lights', 1],
    ['Lantern Season High Water', 1], ['Copper Street', 0.55], ['Perihelion', 0.4], ['Lantern Season Upstream', 0.38]];
  const start = Date.now() - watched.length * 3600_000;
  for (const [i, [name, share]] of watched.entries()) {
    const item = find(name);
    await api('POST', `/UserItems/${item.Id}/UserData?userId=${userId}`, {
      Played: share === 1, PlayCount: share === 1 ? 1 : 0, PlaybackPositionTicks: share === 1 ? 0 : ticks(item, share),
      LastPlayedDate: new Date(start + i * 3600_000).toISOString(),
    });
  }
  await api('POST', `/UserFavoriteItems/${find('Perihelion').Id}?userId=${userId}`);
  await api('POST', `/UserFavoriteItems/${find('Pine Hollow').Id}?userId=${userId}`);
  console.log(`ready: ${items.length} videos for ${user} on ${url}`);
})();
