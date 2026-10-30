const CACHE_NAME = "abv-farm-pwa-v6";
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
        if (event.request.mode === "navigate") {
          return caches.match("./index.html");
        }
      });
    })
  );
});

// ---------- Notifications hors-ligne & background ----------
self.addEventListener("message", (event) => {
  if (event.data && event.data.type === "SHOW_NOTIFICATION") {
    const { title, options } = event.data;
    const notificationOptions = {
      body: options?.body || "",
      icon: options?.icon || "./icon-192.png",
      badge: options?.badge || "./icon-192.png",
      tag: options?.tag || "abv-farm-alert",
      renotify: true,
      vibrate: [200, 100, 200],
      data: { url: options?.url || "./#rappels" }
    };
    event.waitUntil(
      self.registration.showNotification(title, notificationOptions)
    );
  }
});

// Notification click: ouvre ou amène au premier plan l'application ABV Farm
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const targetUrl = event.notification.data?.url || "./#rappels";

  event.waitUntil(
    clients.matchAll({ type: "window", includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ("focus" in client) {
          client.navigate(targetUrl);
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow(targetUrl);
      }
    })
  );
});

