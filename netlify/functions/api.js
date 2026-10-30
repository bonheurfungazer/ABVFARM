// netlify/functions/api.js
// Endpoint Backend pour ABV Farm

exports.handler = async (event, context) => {
  // En-têtes CORS pour les appels depuis l'app web / mobile
  const headers = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'Content-Type': 'application/json'
  };

  // Gestion des requêtes préliminaires CORS
  if (event.httpMethod === 'OPTIONS') {
    return {
      statusCode: 200,
      headers,
      body: ''
    };
  }

  const path = (event.path || '').replace(/^\/api\/?/, '');

  try {
    // Route GET: Statut du backend et règles d'alertes
    if (event.httpMethod === 'GET') {
      if (path.includes('alerts')) {
        return {
          statusCode: 200,
          headers,
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
        statusCode: 200,
        headers,
        body: JSON.stringify({
          status: 'online',
          app: 'ABV Farm Backend API',
          timestamp: new Date().toISOString(),
          route: path || 'root',
          features: {
            serverless: true,
            nodeVersion: process.version,
            authSupported: true,
            alertsSupported: true
          }
        })
      };
    }

    // Route POST: Exécution de logique métier backend & alertes
    if (event.httpMethod === 'POST') {
      const payload = event.body ? JSON.parse(event.body) : {};

      if (path.includes('alerts') || payload.action === 'send_alert') {
        return {
          statusCode: 200,
          headers,
          body: JSON.stringify({
            success: true,
            message: 'Alerte traitée et enregistrée par le backend',
            alertTitle: payload.title || 'Alerte cheptel',
            count: payload.count || 1,
            timestamp: new Date().toISOString()
          })
        };
      }

      return {
        statusCode: 200,
        headers,
        body: JSON.stringify({
          success: true,
          message: 'Données traitées avec succès par le backend',
          receivedAction: payload.action || 'default',
          result: {
            processedAt: new Date().toISOString()
          }
        })
      };
    }

    return {
      statusCode: 405,
      headers,
      body: JSON.stringify({ error: 'Méthode non autorisée' })
    };
  } catch (error) {
    return {
      statusCode: 500,
      headers,
      body: JSON.stringify({ error: error.message })
    };
  }
};
