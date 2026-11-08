const CACHE_NAME = "abv-farm-pwa-v27";
/* m100 : cache CDN séparé — avant, les scripts CDN partageaient CACHE_NAME
   et l'activation purgeait TOUS les caches de l'origine sans préfixe. */
const CDN_CACHE_NAME = "abv-farm-cdn-v1";
const CACHE_PREFIX = "abv-farm-";
/* M83 : précache allégé — icon-512 (268 Ko) et le logo 1 280×1 280 (186 Ko)
   ne sont plus précachés : ils sont chargés à la demande (branche générique
   ci-dessous) et mis en cache à ce moment-là. Le logo réellement affiché
   par l'interface est la version 128×128 (M84, ~5 Ko). */
const ASSETS_TO_CACHE = [
  "./",
  "./index.html",
  "./manifest.json",
  "./abvnewlogo-128.jpg",
  "./icon-192.png"
];

// C25 : SDK Firebase (URLs versionnees, memes que index.html) precaches a
// l'installation, pour qu'un demarrage froid hors-ligne ait quand meme
// Firebase. Sans cela, `firebase` etait indefini a la premiere ouverture
// sans reseau et la synchro restait morte toute la session.
const FIREBASE_SDK_ASSETS = [
  "https://www.gstatic.com/firebasejs/10.8.0/firebase-app-compat.js",
  "https://www.gstatic.com/firebasejs/10.8.0/firebase-auth-compat.js",
  "https://www.gstatic.com/firebasejs/10.8.0/firebase-firestore-compat.js"
];

/* M37 + M103 : jsPDF/AutoTable précachés à l'installation (URLs/version
   EXACTES, les mêmes que ensureJsPdf() dans index.html) pour que l'export
   PDF fonctionne hors-ligne dès le premier usage — avant, il fallait un
   premier chargement en ligne réussi. */
const JSPDF_ASSETS = [
  "https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js",
  "https://cdnjs.cloudflare.com/ajax/libs/jspdf-autotable/3.8.2/jspdf.plugin.autotable.min.js"
];

// Install: Cache core application assets
self.addEventListener("install", (event) => {
  event.waitUntil(
    (async () => {
      /* m101 (mineur) : le cœur applicatif est désormais STRICT — avant,
         chaque échec de précache était toléré et skipWaiting activait quand
         même le SW : un précache incomplet passait en production et le repli
         hors-ligne pouvait ne rien renvoyer. Si un asset cœur échoue,
         l'installation échoue (l'ancien SW reste actif). Les CDN (SDK
         Firebase, jsPDF) restent tolérants : cross-origin, leur échec ne
         doit pas bloquer l'installation, ils seront retentés à l'usage. */
      const cache = await caches.open(CACHE_NAME);
      await cache.addAll(ASSETS_TO_CACHE);
      const cdnCache = await caches.open(CDN_CACHE_NAME);
      const results = await Promise.all(FIREBASE_SDK_ASSETS.concat(JSPDF_ASSETS).map((url) => cdnCache.add(url).catch((err) => {
        console.warn("PWA install: asset CDN non mis en cache:", url, err);
        return null;
      })));
      const failed = results.filter((r) => r === null).length;
      if (failed) console.warn(`PWA install: ${failed} asset(s) CDN non mis en cache, installation poursuivie (retentes a l'usage).`);
      /* M101 : PAS de skipWaiting() ici. Avant, il était inconditionnel et
         contredisait tout le mécanisme waiting/bannière (C26/C27) : la
         stratégie retenue est « waiting + bannière » — le nouveau SW attend,
         la page affiche la bannière, et son bouton « Recharger » poste
         SKIP_WAITING (voir le handler message ci-dessous). */
    })()
  );
});

// Activate: Clean up old caches
self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.map((key) => {
          /* m100 : ne purger QUE nos caches (préfixe abv-farm-), jamais les
             autres caches de l'origine, et conserver le cache CDN courant. */
          if (key.startsWith(CACHE_PREFIX) && key !== CACHE_NAME && key !== CDN_CACHE_NAME) {
            return caches.delete(key);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

/* M103 : stale-while-revalidate borné aux URLs CDN connues — la réponse en
   cache est servie, mais une revalidation réseau part en arrière-plan et ne
   remplace le cache que si la réponse est réussie (200 ou opaque). Avant,
   jsPDF était en cache-first éternel : une version vérolée servie un jour
   restait servie pour toujours. */
function cdnStaleWhileRevalidate(event) {
  event.respondWith(
    caches.open(CDN_CACHE_NAME).then((cache) => cache.match(event.request).then((cached) => {
      const network = fetch(event.request).then((res) => {
        if (res && (res.status === 200 || res.type === "opaque")) {
          cache.put(event.request, res.clone());
        }
        return res;
      });
      if (cached) {
        network.catch(() => {});
        return cached;
      }
      return network.catch(() => new Response("", { status: 504, statusText: "Hors ligne" }));
    }))
  );
}

// Fetch: network-first pour les navigations/index.html (C26), stale-while-
// revalidate reserve aux assets statiques/images, network-first pour remote/APIs
self.addEventListener("fetch", (event) => {
  /* m99 : aucun traitement des méthodes non-GET — avant, un POST (ex.
     /api/audit) traversait le SW et le cache.put levait une erreur (promesse
     non gérée), et l'exclusion reposait sur des sous-chaînes fragiles. */
  if (event.request.method !== "GET") return;
  const url = new URL(event.request.url);
  if (url.origin === self.location.origin && (url.pathname.startsWith("/api/") || url.pathname.startsWith("/.netlify/"))) {
    return;
  }

  // C26 : navigations et index.html en NETWORK-FIRST. Avant, le SWR servait
  // d'abord le cache : chaque nouvelle version arrivait une visite en retard
  // et le bouton « Recharger » pouvait resservir l'ancienne page. Ici on
  // essaie le reseau d'abord (et on rafraichit le cache), repli cache
  // uniquement hors-ligne.
  const isNavigation = event.request.mode === "navigate" || event.request.destination === "document";
  const isIndexHtml = url.origin === self.location.origin && (url.pathname.endsWith("/") || url.pathname.endsWith("/index.html"));
  if (isNavigation || isIndexHtml) {
    event.respondWith(
      fetch(event.request).then((networkResponse) => {
        if (networkResponse && networkResponse.status === 200) {
          const responseToCache = networkResponse.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put("./index.html", responseToCache));
        }
        return networkResponse;
      }).catch(() => caches.match(event.request).then((cached) => cached || caches.match("./index.html")).then((fallback) => fallback || new Response("Hors ligne", { status: 503, statusText: "Hors ligne" })))
    );
    return;
  }

  /* m87 : Google Fonts mis en cache (SWR) — avant, l'exclusion « google »
     ci-dessous empêchait toute mise en cache : au premier lancement
     hors-ligne, repli sur les polices système. La feuille CSS et les
     fichiers de police sont versionnés par Google, le SWR est sûr. */
  if (url.host === "fonts.googleapis.com" || url.host === "fonts.gstatic.com") {
    cdnStaleWhileRevalidate(event);
    return;
  }

  // Skip caching for Firebase APIs or cloud Firestore network requests
  if (url.origin.includes("firebase") || url.origin.includes("googleapis") || url.origin.includes("google")) {
    return;
  }
  /* M103 : jsPDF/AutoTable — URLs/version exactes uniquement, en SWR borné
     (voir cdnStaleWhileRevalidate). */
  if (JSPDF_ASSETS.includes(url.href)) {
    cdnStaleWhileRevalidate(event);
    return;
  }
  // Autres scripts CDN : SWR borné, mise en cache des seules réponses réussies
  if (url.host.includes("cdn.jsdelivr.net") || url.host.includes("cdnjs.cloudflare.com")) {
    cdnStaleWhileRevalidate(event);
    return;
  }

  // C25 : SDK Firebase servis depuis le cache (reponses cross-origin de type
  // "cors", jamais stockees par la branche generique qui exige "basic") :
  // cache d'abord, reseau en secours avec mise en cache de la reponse.
  if (url.host === "www.gstatic.com" && url.pathname.includes("/firebasejs/")) {
    event.respondWith(
      caches.open(CDN_CACHE_NAME).then((cache) => cache.match(event.request).then((cached) => cached || fetch(event.request).then((res) => { if (res && (res.status === 200 || res.type === "opaque")) { cache.put(event.request, res.clone()); } return res; }).catch(() => new Response("", { status: 504, statusText: "Hors ligne" }))))
    );
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
        /* m101 (mineur) : ne jamais résoudre undefined — avant, un échec
           hors-ligne sans cache laissait respondWith sans réponse. */
        return new Response("", { status: 504, statusText: "Hors ligne" });
      });
    })
  );
});

// ---------- Notifications locales (declenchees par la page ouverte) ----------
// Aucun push serveur : app fermee, aucune notification ne peut arriver (C6).
self.addEventListener("message", (event) => {
  // C26 : la banniere « Recharger » poste SKIP_WAITING puis attend
  // controllerchange avant de recharger, pour ne pas resservir l'ancienne page.
  if (event.data && event.data.type === "SKIP_WAITING") {
    self.skipWaiting();
    return;
  }
  if (event.data && event.data.type === "SHOW_NOTIFICATION") {
    const { title, options } = event.data;
    const notificationOptions = {
      body: options?.body || "",
      icon: options?.icon || "./icon-192.png",
      badge: options?.badge || "./icon-192.png",
      tag: options?.tag || "abv-farm-alert",
      renotify: true,
      /* m8 : vibration selon la préférence transmise par la page — avant,
         [200,100,200] était inconditionnel et ignorait prefVibrate. */
      vibrate: options?.vibrate === false ? [] : (Array.isArray(options?.vibrate) ? options.vibrate : [200, 100, 200]),
      data: { url: options?.url || "./#rappels" }
    };
    /* m24 : promesse rattrapée — avant, un échec de showNotification était
       silencieux (ni retour, ni catch). */
    event.waitUntil(
      self.registration.showNotification(title, notificationOptions).catch((err) => {
        console.warn("PWA: affichage de la notification impossible:", err);
      })
    );
  }
});

// Notification click: ouvre ou amène au premier plan l'application ABV Farm
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const targetUrl = event.notification.data?.url || "./#rappels";

  event.waitUntil(
    clients.matchAll({ type: "window", includeUncontrolled: true }).then(async (clientList) => {
      /* m102 : navigation ATTENDUE et client de l'app préféré — avant,
         navigate() relatif partait sans être attendu sur le premier client
         venu, et le contexte en cours (modale ouverte) pouvait être perdu
         sans que l'échec soit vu. */
      const appClient = clientList.find((c) => c.url && c.url.startsWith(self.location.origin)) || clientList[0];
      if (appClient) {
        try {
          if ("navigate" in appClient) await appClient.navigate(targetUrl);
        } catch (err) {
          console.warn("PWA: navigation du client existant impossible, focus seul:", err);
        }
        if ("focus" in appClient) return appClient.focus();
        return undefined;
      }
      if (clients.openWindow) {
        return clients.openWindow(targetUrl);
      }
      return undefined;
    })
  );
});
