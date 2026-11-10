// netlify/functions/api.js
// Endpoint Backend pour ABV Farm
//
// Routes :
//   GET  /api/          -> statut du backend
//   GET  /api/alerts    -> regles d'alertes
//   GET    /api/echeances/<jeton>  -> dernier releve d'echeances transmis par l'app
//   POST   /api/echeances/<jeton>  -> l'app y depose ses echeances (7 jours a venir)
//   DELETE /api/echeances/<jeton>  -> desactivation des alertes Telegram
// Le <jeton> est un secret aleatoire (32 caracteres hex) propre a l'appareil
// de Mr Robot : il tient lieu d'identifiant ET d'autorisation, pour cette
// seule liste (dates + libelles, aucune autre donnee). Stockage : Netlify Blobs.
//
// Note : l'endpoint POST /api/audit (audit complet des donnees de l'elevage)
// a ete retire le 2026-11-08 a la demande de Mr Robot, en meme temps que la
// section « Audit API » de l'application : le probleme C12 (endpoint public
// sans authentification) est regle par cette suppression.

// Plafond de corps POST (1 Mo) : garde-fou generique, conserve apres le
// retrait de l'endpoint d'audit.
const MAX_BODY_BYTES = 1024 * 1024;

// CORS (section 12) : seule l'origine du site déployé peut lire les réponses
// depuis un navigateur. Les méthodes annoncées sont exactement celles servies
// (GET, POST, OPTIONS) : PUT/DELETE ne sont pas servis et répondent 405.
// ATTENTION : le CORS n'est PAS un contrôle d'accès — l'endpoint reste public
// et sans authentification, et ne stocke rien ; un appel curl direct n'est pas
// bloqué par ces en-têtes, seuls les navigateurs d'autres origines le sont.
/* m25 : origines autorisees CONFIGURABLES (variable d'environnement
   ABV_ALLOWED_ORIGINS, liste separee par des virgules) — avant, une seule
   origine etait codee en dur : tout autre deploiement (preprod, domaine
   personnalise) etait bloque sans toucher au code. Defaut inchange. */
const ALLOWED_ORIGINS = (process.env.ABV_ALLOWED_ORIGINS || 'https://abv-farm.netlify.app').split(',').map((s) => s.trim()).filter(Boolean);
function corsHeaders(event) {
  const origin = (event.headers && (event.headers.origin || event.headers.Origin)) || '';
  const base = { 'Content-Type': 'application/json', 'Vary': 'Origin' };
  if (origin && ALLOWED_ORIGINS.includes(origin)) {
    return { ...base, 'Access-Control-Allow-Origin': origin, 'Access-Control-Allow-Headers': 'Content-Type', 'Access-Control-Allow-Methods': 'GET, POST, DELETE, OPTIONS' };
  }
  return base;
}

/* Echeances Telegram (2026-11-10) : stockage cle/valeur Netlify Blobs.
   Chargement tolerant : si le module est absent, les routes /api/echeances
   repondent 503 sans affecter le reste de l'API. */
async function echeancesStore(event) {
  try {
    const blobs = require('@netlify/blobs');
    if (blobs && typeof blobs.connectLambda === 'function' && event) { try { blobs.connectLambda(event); } catch (e) {} }
    return blobs.getStore('echeances');
  } catch (e) { console.error('ABV Farm API : Blobs indisponible', e); return null; }
}

exports.handler = async (event, context) => {
  const headers = corsHeaders(event);
  if (event.httpMethod === 'OPTIONS') {
    // Pré-vol : sans origine autorisée, pas d'en-tête CORS -> le navigateur bloque.
    if (!headers['Access-Control-Allow-Origin']) return { statusCode: 403, headers, body: JSON.stringify({ success: false, error: 'ORIGIN_NOT_ALLOWED', message: 'Origine non autorisée' }) };
    return { statusCode: 200, headers, body: '' };
  }

  /* M30 : routage sur le DERNIER segment exact du chemin uniquement — avant,
     rawPath = path + ' ' + rawUrl et includes('audit') : un paramètre ou un
     segment contenant « audit » détournait la route. */
  const _segments = (event.path || '').split('/').filter(Boolean);
  const _lastSeg = (_segments[_segments.length - 1] || '').toLowerCase();
  const rawPath = event.path || '';
  const query = event.queryStringParameters || {};
  const isAlerts = _lastSeg === 'alerts';
  /* /api/echeances/<jeton> : le segment precedent doit etre « echeances »
     et le dernier un jeton de 32 caracteres hexadecimaux. */
  const isEcheances = _segments.length >= 2 && (_segments[_segments.length - 2] || '').toLowerCase() === 'echeances' && /^[a-z0-9]{32}$/.test(_lastSeg);

  try {
    if (event.httpMethod === 'GET') {
      if (isEcheances) {
        const store = await echeancesStore(event);
        if (!store) return { statusCode: 503, headers, body: JSON.stringify({ success: false, error: 'STORAGE_UNAVAILABLE', message: 'Stockage des échéances indisponible.' }) };
        const raw = await store.get(_lastSeg);
        let digest = null; try { digest = raw ? JSON.parse(raw) : null; } catch (e) { digest = null; }
        return { statusCode: 200, headers, body: JSON.stringify({ success: true, digest }) };
      }
      if (isAlerts) {
        return {
          statusCode: 200, headers,
          body: JSON.stringify({
            status: 'online',
            service: 'ABV Farm Alert Engine',
            rules: [
              { code: 'PALPATION', window: 'J10-J14', label: 'Palpation de confirmation de gestation' },
              { code: 'NEST_BOX', day: 'J28', label: 'Mise en place de la boîte à nid' },
              { code: 'KINDLE', day: 'J31', label: 'Mise bas attendue' },
              { code: 'WEANING', window: 'J35-J42', label: 'Sevrage des lapereaux' },
              { code: 'FEED_STOCK', threshold: 10, unit: 'kg', label: 'Alerte stock critique' },
              { code: 'TREATMENTS', label: 'Rappels de vaccins et délai d\'attente sanitaire' }
            ],
            offlineCapable: true
          })
        };
      }
      return {
        statusCode: 200, headers,
        body: JSON.stringify({
          status: 'online',
          app: 'ABV Farm Backend API',
          timestamp: new Date().toISOString(),
          route: rawPath.trim() || 'root',
          /* m26 : avant, la route racine annoncait authSupported: true (aucune
             authentification n'existe sur cette API) et exposait la version
             exacte de Node (process.version) — information inutile aux clients
             et utile a un attaquant. */
          features: { serverless: true, authSupported: false, alertsSupported: true }
        })
      };
    }

    if (event.httpMethod === 'POST') {
      /* m29 : corps base64 (isBase64Encoded) decode AVANT mesure et parsing —
         avant, il traversait tel quel (JSON illisible) ; et la taille etait
         mesuree en CARACTERES, pas en octets : le plafond « 1 Mo » etait
         approximatif pour tout contenu non-ASCII. */
      let rawBody = event.body || '';
      if (event.isBase64Encoded && rawBody) {
        try { rawBody = Buffer.from(rawBody, 'base64').toString('utf8'); } catch (e) { rawBody = ''; }
      }
      if (Buffer.byteLength(rawBody, 'utf8') > MAX_BODY_BYTES) {
        return { statusCode: 413, headers, body: JSON.stringify({ success: false, error: 'PAYLOAD_TOO_LARGE', message: 'Corps trop volumineux (limite 1 Mo).' }) };
      }
      if (isEcheances && Buffer.byteLength(rawBody, 'utf8') > 65536) {
        return { statusCode: 413, headers, body: JSON.stringify({ success: false, error: 'PAYLOAD_TOO_LARGE', message: 'Échéances trop volumineuses (limite 64 Ko).' }) };
      }
      let payload;
      try { payload = rawBody ? JSON.parse(rawBody) : {}; }
      catch (e) { return { statusCode: 400, headers, body: JSON.stringify({ success: false, error: 'INVALID_JSON', message: 'Corps JSON invalide.' }) }; }
      if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
        return { statusCode: 400, headers, body: JSON.stringify({ success: false, error: 'INVALID_PAYLOAD', message: 'Corps JSON invalide : un objet est attendu.' }) };
      }
      if (isEcheances) {
        const items = Array.isArray(payload.items) ? payload.items : null;
        const valid = payload.v === 1 && !!items && items.length <= 100 && items.every((i) => i && typeof i.d === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(i.d) && typeof i.t === 'string' && i.t.length > 0 && i.t.length <= 140);
        if (!valid) return { statusCode: 400, headers, body: JSON.stringify({ success: false, error: 'INVALID_DIGEST', message: "Format d'échéances invalide." }) };
        const store = await echeancesStore(event);
        if (!store) return { statusCode: 503, headers, body: JSON.stringify({ success: false, error: 'STORAGE_UNAVAILABLE', message: 'Stockage des échéances indisponible.' }) };
        await store.set(_lastSeg, JSON.stringify({ v: 1, at: typeof payload.at === 'string' ? payload.at : new Date().toISOString(), items: items.map((i) => ({ d: i.d, t: i.t })) }));
        return { statusCode: 200, headers, body: JSON.stringify({ success: true, stored: true, count: items.length }) };
      }
      if (isAlerts || payload.action === 'send_alert') {
        return {
          statusCode: 501, headers,
          body: JSON.stringify({
            success: false,
            error: 'NOT_IMPLEMENTED',
            message: "Envoi de notification non configuré : les alertes restent visibles dans l'application uniquement.",
            alertTitle: payload.title || 'Alerte cheptel',
            count: payload.count || 1,
            timestamp: new Date().toISOString()
          })
        };
      }
      /* M27 : avant, toute action POST inconnue répondait 200 « succès »
         alors que rien n'avait été traité. */
      return {
        statusCode: 400, headers,
        body: JSON.stringify({
          success: false,
          error: 'UNKNOWN_ACTION',
          message: "Action POST non reconnue : seule l'action 'send_alert' est reconnue.",
          receivedAction: payload.action || null
        })
      };
    }

    if (event.httpMethod === 'DELETE') {
      if (isEcheances) {
        const store = await echeancesStore(event);
        if (!store) return { statusCode: 503, headers, body: JSON.stringify({ success: false, error: 'STORAGE_UNAVAILABLE', message: 'Stockage des échéances indisponible.' }) };
        await store.delete(_lastSeg);
        return { statusCode: 200, headers, body: JSON.stringify({ success: true, deleted: true }) };
      }
      return { statusCode: 405, headers: { ...headers, 'Allow': 'GET, POST, DELETE, OPTIONS' }, body: JSON.stringify({ success: false, error: 'METHOD_NOT_ALLOWED', message: 'Suppression disponible uniquement sur /api/echeances/<jeton>.' }) };
    }

    /* m27 : 405 avec en-tete Allow (methodes reellement servies) et format
       d'erreur coherent avec le reste de l'API ({ success, error, message }). */
    return { statusCode: 405, headers: { ...headers, 'Allow': 'GET, POST, DELETE, OPTIONS' }, body: JSON.stringify({ success: false, error: 'METHOD_NOT_ALLOWED', message: 'Méthode non autorisée : utilisez GET, POST, DELETE ou OPTIONS.' }) };
  } catch (error) {
    console.error('ABV Farm API : erreur interne', error);
    return { statusCode: 500, headers, body: JSON.stringify({ success: false, error: 'INTERNAL_ERROR', message: 'Erreur interne.' }) };
  }
};
