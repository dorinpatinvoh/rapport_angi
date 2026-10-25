# Frontend Angular

Voir le README à la racine pour le démarrage complet et les identifiants bootstrap.

- `npm ci` : installer les versions verrouillées.
- `npm start` : ouvrir http://localhost:4200 ; proxy `/api` vers localhost:8080.
- `npm run build` : générer `dist/frontend/browser`.

L'API est nécessaire : session HTTP et protection CSRF, sans stockage du mot de passe ou de jeton d'authentification dans localStorage.
