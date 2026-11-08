// netlify/functions/api.js
// Endpoint Backend pour ABV Farm
//
// Routes :
//   GET  /api/          -> statut du backend
//   GET  /api/alerts    -> regles d'alertes
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
    return { ...base, 'Access-Control-Allow-Origin': origin, 'Access-Control-Allow-Headers': 'Content-Type', 'Access-Control-Allow-Methods': 'GET, POST, OPTIONS' };
  }
  return base;
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

  try {
    if (event.httpMethod === 'GET') {
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
      let payload;
      try { payload = rawBody ? JSON.parse(rawBody) : {}; }
      catch (e) { return { statusCode: 400, headers, body: JSON.stringify({ success: false, error: 'INVALID_JSON', message: 'Corps JSON invalide.' }) }; }
      if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
        return { statusCode: 400, headers, body: JSON.stringify({ success: false, error: 'INVALID_PAYLOAD', message: 'Corps JSON invalide : un objet est attendu.' }) };
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

    /* m27 : 405 avec en-tete Allow (methodes reellement servies) et format
       d'erreur coherent avec le reste de l'API ({ success, error, message }). */
    return { statusCode: 405, headers: { ...headers, 'Allow': 'GET, POST, OPTIONS' }, body: JSON.stringify({ success: false, error: 'METHOD_NOT_ALLOWED', message: 'Méthode non autorisée : utilisez GET, POST ou OPTIONS.' }) };
  } catch (error) {
    console.error('ABV Farm API : erreur interne', error);
    return { statusCode: 500, headers, body: JSON.stringify({ success: false, error: 'INTERNAL_ERROR', message: 'Erreur interne.' }) };
  }
};
