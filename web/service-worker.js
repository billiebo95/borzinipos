// BORZINI PWA service worker — cache-first app shell, offline-first by design.
// Bump CACHE_VERSION whenever any cached file changes so clients pick up the new version.
const CACHE_VERSION = 'borzini-v2';
const APP_SHELL = [
  './',
  './index.html',
  './manifest.webmanifest',
  './css/styles.css',
  './js/app.js',
  './js/db.js',
  './js/money.js',
  './js/core.js',
  './js/state.js',
  './js/router.js',
  './js/toast.js',
  './js/dom.js',
  './js/screens/pos.js',
  './js/screens/checkout.js',
  './js/screens/warehouse.js',
  './js/screens/purchase.js',
  './js/screens/receipts.js',
  './js/screens/catalog.js',
  './js/screens/stats.js',
  './js/screens/settings.js',
  './icons/icon-64.png',
  './icons/icon-180.png',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-192-maskable.png',
  './icons/icon-512-maskable.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_VERSION).then((cache) => cache.addAll(APP_SHELL)).then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_VERSION).map((k) => caches.delete(k))),
    ).then(() => self.clients.claim()),
  );
});

// Cache-first for same-origin GET requests (the whole point of an offline-first cash register);
// network is only consulted for things not yet cached, and successful responses are cached for
// next time. Cross-origin requests (Google APIs during sync) are never intercepted here.
self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;

  event.respondWith(
    caches.match(req).then((cached) => {
      if (cached) return cached;
      return fetch(req)
        .then((res) => {
          if (res && res.ok) {
            const copy = res.clone();
            caches.open(CACHE_VERSION).then((cache) => cache.put(req, copy));
          }
          return res;
        })
        .catch(() => caches.match('./index.html'));
    }),
  );
});
