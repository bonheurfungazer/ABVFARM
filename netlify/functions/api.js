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
    // Route GET: Statut du backend et informations système
    if (event.httpMethod === 'GET') {
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
            authSupported: true
          }
        })
      };
    }

    // Route POST: Exécution de logique métier backend
    if (event.httpMethod === 'POST') {
      const payload = event.body ? JSON.parse(event.body) : {};

      // Exemple d'action backend (calcul de projection, validation, export, etc.)
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
