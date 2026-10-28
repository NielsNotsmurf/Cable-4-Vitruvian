// Offline support for the web app. Everything is served from this folder; there is no server
// to talk to. Pages, scripts and data: network first, so updates arrive as soon as you are
// online, with the cached copy when offline. Content-hashed files (.wasm) never change, so
// they come from the cache first. Only this app's own origin is cached.
const CACHE = 'vitruvian-web-v1';
const SHELL = ['./', 'index.html', 'pwa.js', 'vitruvian.js', 'manifest.webmanifest',
  'exercise_dump.json', 'one_rep_max_map.json', 'fonts/symbols.ttf', 'icons/icon-192.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) return;
  const store = res => {
    if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
    return res;
  };
  if (req.url.endsWith('.wasm')) {
    e.respondWith(caches.match(req).then(hit => hit || fetch(req).then(store)));
  } else {
    e.respondWith(fetch(req).then(store).catch(() => caches.match(req, { ignoreSearch: true })));
  }
});
