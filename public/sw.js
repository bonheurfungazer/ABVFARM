const CACHE_NAME = "abv-farm-pwa-v2";
const ASSETS_TO_CACHE = [
  "./",
  "./index.html",
  "./manifest.json",
  "./abvnewlogo.jpg",
  "./icon-192.png",
  "./icon-512.png"
];

// Install: Cache core application assets
self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(ASSETS_TO_CACHE);
    }).then(() => self.skipWaiting())
  );
});

// Activate: Clean up old caches
self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.map((key) => {
          if (key !== CACHE_NAME) {
            return caches.delete(key);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

// Fetch: Stale-while-revalidate for local assets, network-first for remote/APIs
self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);

  // Skip caching for Firebase APIs or cloud Firestore network requests
  if (url.origin.includes("firebase") || url.origin.includes("googleapis") || url.origin.includes("google")) {
    return;
  }

  event.respondWith(
    caches.match(event.request).then((cachedResponse) => {
      if (cachedResponse) {
        // Fetch in background to update cache
        fetch(event.request).then((networkResponse) => {
          if (networkResponse && networkResponse.status === 200) {
            caches.open(CACHE_NAME).then((cache) => cache.put(event.request, networkResponse));
          }
        }).catch(() => {});
        return cachedResponse;
      }
      return fetch(event.request).then((networkResponse) => {
        if (!networkResponse || networkResponse.status !== 200 || networkResponse.type !== "basic") {
          return networkResponse;
        }
        const responseToCache = networkResponse.clone();
        caches.open(CACHE_NAME).then((cache) => cache.put(event.request, responseToCache));
        return networkResponse;
      }).catch(() => {
        // Fallback for navigation requests
        if (event.request.mode === "navigate") {
          return caches.match("./index.html");
        }
      });
    })
  );
});
