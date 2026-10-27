# Rapports terrain - ANGI DSN PRO SARL

Angular 20 + Spring Boot 4 / Java 21 : comptes commerciaux AZO et Zehami, rapports quotidiens et suivi par le super administrateur. Interface adaptée au téléphone, à la tablette et à l'ordinateur, avec les couleurs et les logos fournis par ANGI.

## Comptes et inscription

- Le premier SUPER_ADMIN se crée par variables d'environnement (voir le démarrage ci-dessous). Un compte existant n'est pas recréé.
- Le lien **Créer mon compte** de la connexion ouvre `/inscription`. Le commercial saisit prénom, nom, email, mot de passe et équipe. Le compte reste inactif jusqu'à validation.
- Connecté comme SUPER_ADMIN, ouvrir **Commerciaux**. Activer la demande, choisir l'équipe et renseigner si disponible le code de parrainage de l'application mobile.
- Pour créer directement un commercial, ouvrir **Commerciaux > Créer directement un commercial**, remplir les champs et cliquer **Créer et activer le compte**. Transmettre personnellement l'email et le mot de passe initial au commercial. Aucun email automatique n'est envoyé.
- Un code de parrainage doit être unique dans son équipe. Il doit déjà exister dans l'application AZO ou Zehami : cette application ne crée pas de comptes dans les applications mobiles.
- Les mots de passe sont stockés avec BCrypt. Aucune inscription publique ne permet de choisir un rôle administrateur ou de s'attribuer un code de parrainage.

## Rapport sur mobile

1. Se connecter, ouvrir **Rapports > Rédiger un rapport**.
2. Choisir la journée et le mode : compte rendu écrit, ou statistiques avec observations facultatives.
3. En mode écrit, raconter les zones visitées, les besoins, les résultats, les difficultés et les prochaines actions.
4. En mode statistiques, saisir les prospects contactés et les inscriptions attribuées. Sans API, les chiffres sont explicitement déclaratifs. Si l'API de l'équipe est configurée, cocher l'import ; les chiffres sont récupérés de nouveau côté serveur à l'enregistrement.
5. Enregistrer en brouillon ou soumettre au super administrateur. Après soumission, le contenu est verrouillé.
6. Le SUPER_ADMIN ouvre **Lire le rapport**, puis confirme le rapport ou utilise **Renvoyer pour confirmation**, avec un commentaire obligatoire. Le commercial consulte le commentaire, corrige/complète et soumet à nouveau. L'historique des retours est conservé.
7. Le bouton **PDF** télécharge un document avec le logo ANGI, celui de l'équipe, le nom du commercial, la journée, les chiffres et leur origine, les observations et le suivi. Les longues descriptions passent sur plusieurs pages.

Un commercial ne voit que ses rapports. ADMIN et SUPER_ADMIN peuvent lire tous les rapports et leurs PDF ; seul SUPER_ADMIN gère les commerciaux et examine les soumissions. Seul l'auteur peut modifier son contenu ; seuls les brouillons sont supprimables. Un super administrateur ne peut pas confirmer ses propres rapports. Les conflits entre deux versions ouvertes simultanément sont refusés plutôt que d'écraser silencieusement une modification.

## Démarrage sous Windows PowerShell

Prérequis : JDK 21, Node 24, PostgreSQL (ou Docker Desktop démarré).

Dans le terminal du backend, définir les informations de connexion à PostgreSQL si elles diffèrent des valeurs locales par défaut :

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5434/rapport_angi"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = Read-Host "Mot de passe PostgreSQL"
```

Pour une **nouvelle** base via Docker, depuis la racine : `docker compose up -d db`. Pour une base déjà existante, utiliser ses coordonnées et conserver ses données.

Créer le premier administrateur avec un email nouveau (inutile si votre compte SUPER_ADMIN fonctionne déjà) :

```powershell
$env:BOOTSTRAP_ADMIN_EMAIL = "votre-email@example.com"
$secret = Read-Host "Mot de passe initial admin (12 à 72 caractères ASCII)" -AsSecureString
$env:BOOTSTRAP_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new("", $secret).Password
cd backend
.\mvnw.cmd spring-boot:run
```

Après la création, retirer les variables bootstrap du terminal lors du prochain redémarrage :

```powershell
Remove-Item Env:BOOTSTRAP_ADMIN_EMAIL -ErrorAction SilentlyContinue
Remove-Item Env:BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
.\mvnw.cmd spring-boot:run
```

Dans un second terminal :

```powershell
cd D:\rapport\frontend
npm ci
npm start
```

Ouvrir <http://localhost:4200>. Sous Linux/macOS, utiliser `export VARIABLE=...` et `bash mvnw spring-boot:run`.

## Mise à jour de la base du prototype

La migration V3 conserve les utilisateurs et rapports existants. Les anciens commerciaux n'ont pas d'équipe : les affecter via **Commerciaux**. Les anciens rapports restent identifiés « équipe non affectée » tant qu'un brouillon n'a pas été repris par son auteur ; aucune équipe n'est inventée pour l'historique.

Si les migrations V1/V2 fonctionnent déjà, démarrer normalement : V3 s'applique automatiquement. Ne pas modifier les migrations déjà exécutées.

Si les tables existent **sans** `flyway_schema_history`, sauvegarder la base et vérifier leur conformité avec `backend/src/main/resources/db/migration/V1__init_schema.sql`. Après cette vérification seulement, démarrer une fois avec :

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.flyway.baseline-on-migrate=true --spring.flyway.baseline-version=1"
```

Les anciens mots de passe en clair ne sont plus acceptés. Créer si nécessaire un nouvel administrateur avec un autre email via le bootstrap. Un mot de passe BCrypt existant n'est pas réinitialisé par le bootstrap.

## Connexion aux applications mobiles

Le connecteur est présent, mais reste désactivé sans configuration. Il faut une API **serveur à serveur** sur chaque application, protégée par clé, qui renvoie les statistiques **de la journée**, et non les compteurs cumulés. Le code de parrainage est renseigné par SUPER_ADMIN.

Variables disponibles :

| Équipe | Adresse de l'endpoint | Clé Bearer |
| --- | --- | --- |
| AZO | `AZO_STATS_URL` | `AZO_STATS_API_KEY` |
| Zehami | `ZEHAMI_STATS_URL` | `ZEHAMI_STATS_API_KEY` |

Requête envoyée par le backend :

```http
GET <endpoint-configuré>?referralCode=<code-du-compte>&date=2026-10-25
Authorization: Bearer <clé-serveur>
```

Contrat JSON attendu (valeurs d'exemple uniquement) :

```json
{
  "team": "AZO",
  "referralCode": "CODE-001",
  "date": "2026-10-25",
  "prospectsContacted": 12,
  "registrations": 3
}
```

Le serveur mobile doit valider la clé et calculer les inscriptions attribuées au code pour cette journée, selon la même convention de date (Africa/Lagos). Si l'application mobile ne stocke pas les prospects contactés, son API devra être adaptée avec la définition métier choisie ; aucun chiffre manquant n'est fabriqué ici. Les clés ne sont jamais envoyées à Angular. Le backend valide l'équipe, le code, la date et les nombres ; un échec ou une configuration absente ne produit pas un faux bilan à zéro. Les données enregistrées sont des instantanés, avec date d'import, et ne changent pas quand les compteurs de l'application mobile évoluent.

L'adaptation des backends AZO/Zehami et la fourniture des clés restent à faire avec leurs API réelles. HTTPS est obligatoire hors localhost. Les redirections ne sont pas suivies ; les requêtes ont des délais limités.

## API

| Méthode | Route | Accès / usage |
| --- | --- | --- |
| GET | `/api/auth/csrf` | Cookie CSRF |
| POST | `/api/auth/register` | Inscription commerciale en attente |
| POST | `/api/auth/login` | Form URL encodé : `email`, `password` |
| GET / POST | `/api/auth/me`, `/api/auth/logout` | Profil / fin de session |
| GET / POST | `/api/commercials` | SUPER_ADMIN : liste / création activée |
| PUT | `/api/commercials/{id}` | SUPER_ADMIN : équipe, code, activation |
| GET / POST | `/api/reports` | Rapports autorisés / création |
| GET / PUT / DELETE | `/api/reports/{id}` | Lecture / modification de brouillon ou retour / suppression de brouillon |
| POST | `/api/reports/{id}/review` | SUPER_ADMIN : action `RETURNED` ou `CONFIRMED`, commentaire et version |
| GET | `/api/reports/{id}/pdf` | PDF avec les mêmes contrôles d'accès que la lecture |
| GET | `/api/integrations/status`, `/api/integrations/my-stats?date=...` | Configuration / statistiques du compte connecté |

POST, PUT et DELETE nécessitent le cookie de session et `X-XSRF-TOKEN`, géré par Angular pour les URL relatives. Un rapport accepte `title`, `content`, `mode` (`WRITTEN`/`STATS`), `reportDate`, `status` (`DRAFT`/`SUBMITTED`), les deux compteurs et `syncStats`. PUT et review nécessitent la `version` retournée par GET. L'auteur, la source vérifiée et l'équipe commerciale sont déterminés côté serveur.

## Vérifications

```powershell
cd backend
.\mvnw.cmd verify
cd ..\frontend
npm ci
npm run build
```

Les tests couvrent sessions, CSRF, inscription en attente, activation, permissions, unicité des codes, transitions et conflits de version, origine des statistiques, contrat API mobile, accès et contenu des PDF et pagination. Les migrations V1/V2/V3 sont testées sur H2 en mode PostgreSQL.

Sur Linux/CI avec Java 21, un test navigateur démarre automatiquement une base H2 isolée et teste l'interface réelle :

```bash
cd frontend
npm ci
npm run build
npx playwright install --with-deps chromium
npm run test:smoke
```

Ce test crée uniquement des comptes fictifs dans une base H2 en mémoire ; il n'utilise pas votre PostgreSQL. Les ports 8080 et 4200 doivent être libres. Le parcours couvre inscription, activation, création commerciale, rapport écrit sur mobile, retour, bilan chiffré, nouvelle soumission, confirmation, PDF et déconnexion, avec vérification des largeurs 360/390/768/1440 px.

En déploiement, servir Angular et `/api` sous la même origine via reverse proxy, utiliser HTTPS et `SESSION_COOKIE_SECURE=true`, fournir les secrets par l'environnement. Le bundle Angular seul ne contient pas le backend. La pagination et la limitation générale des tentatives de connexion ne sont pas encore implémentées. Les demandes de lien de réinitialisation sont espacées d’au moins une minute par email sur chaque instance. Les images de marque fournies sont conservées ; la police PDF DejaVu est accompagnée de sa licence.


## Mot de passe oublié

Le lien **Mot de passe oublié ?** est disponible sur la connexion. L'utilisateur reçoit un lien valable 30 minutes, utilisable une fois, et choisit son nouveau mot de passe. La réponse ne confirme pas l'existence d'un compte. Le jeton aléatoire est stocké uniquement sous forme d'empreinte SHA-256 ; le changement du mot de passe invalide les anciennes sessions. Un compte inactif reste inactif.

Pour activer l'envoi, configurer les variables SMTP dans le terminal qui démarre le backend :

```powershell
$env:SMTP_HOST = "adresse-smtp-de-votre-fournisseur"
$env:SMTP_PORT = "587"
$env:SMTP_USERNAME = "votre-identifiant-smtp"
$secretSmtp = Read-Host "Mot de passe ou clé SMTP" -AsSecureString
$env:SMTP_PASSWORD = [System.Net.NetworkCredential]::new("", $secretSmtp).Password
$env:SMTP_FROM = "adresse-expediteur-validee@votre-domaine.com"
$env:APP_PUBLIC_URL = "http://localhost:4200"
```

Ces adresses sont des exemples à remplacer. En ligne, `APP_PUBLIC_URL` doit être l'adresse HTTPS réelle du frontend, sans paramètre ni fragment. Par défaut SMTP utilise authentification et STARTTLS (port 587). Pour le port 465, utiliser `SMTP_SSL=true` et `SMTP_STARTTLS=false` selon le fournisseur. Redémarrer le backend après configuration. Aucun fournisseur email réel n'est configuré par cette mise à jour et aucun email utilisateur n'a été envoyé pendant les tests.

Sans SMTP, le formulaire indique que l'envoi est indisponible. Le SUPER_ADMIN peut néanmoins ouvrir **Commerciaux > [compte] > Réinitialiser le mot de passe**, saisir et confirmer le nouveau mot de passe, puis le transmettre personnellement. Cette assistance ne permet pas de réinitialiser un compte administrateur depuis le navigateur. Le SUPER_ADMIN peut utiliser le lien email ou la procédure locale ci-dessous.

API : POST `/api/auth/forgot-password` avec `{email}`, POST `/api/auth/reset-password` avec `{token,password}`, et POST `/api/commercials/{id}/password` avec `{password}` (SUPER_ADMIN). Ces écritures restent protégées par CSRF.

## Profils à compter dans les bilans AZO

La grille tarifaire fournie distingue les profils suivants : clients ; Speeders moto essence, moto électrique, tricycle, Gazelle, Koala et Léopard ; livreurs express ; coursiers personnels ; agences PRO, SILVER, OR, DIAMANT, PLATINE et ELITE ; boutiques partenaires. Les types de colis, commissions et options de téléphone ne sont pas des inscriptions supplémentaires.

En mode statistiques AZO, cocher **Détailler les profils AZO**, puis renseigner les compteurs par groupe. Les **comptes inscrits (uniques)** restent un indicateur séparé : un même compte pouvant activer plusieurs activités, la somme des profils ne remplace pas le nombre de personnes inscrites. Les nombres correspondent aux profils enregistrés/activés dans la journée, pas au nombre de courses ou livraisons effectuées. Les rapports Zehami gardent leurs compteurs génériques, faute de nomenclature Zehami fournie.

La répartition est conservée dans le rapport et reprise dans la lecture et le PDF. Les anciens rapports restent sans répartition, explicitement indiquée comme non fournie. L'API des profils est GET `/api/report-profiles`. Les créations/modifications acceptent `profileCounts`, objet dont les clés sont les codes renvoyés par cette API et les valeurs des entiers entre 0 et 1 000 000.

Le contrat des API mobiles peut aussi fournir `profileCounts`, par exemple `{"CLIENT":4,"SPEEDER_MOTO_ESSENCE":2,"AGENCE_PRO":1}`. Une répartition absente n'est jamais transformée en chiffres vérifiés à zéro. Avec l'import actif, seule la répartition renvoyée par l'API est enregistrée ; les valeurs du navigateur sont ignorées. La migration V4 ajoute ces compteurs et la réinitialisation sans effacer les données.


## Récupérer localement le compte SUPER_ADMIN sans email

Cette procédure nécessite l'accès au serveur et à sa configuration de base de données. Elle agit uniquement sur un SUPER_ADMIN actif déjà existant ; une erreur d'email ne crée pas de nouveau compte.

Arrêter le backend, puis dans PowerShell :

```powershell
cd D:\rapport
git pull origin main
cd backend
$env:BOOTSTRAP_ADMIN_EMAIL = "email-exact-du-super-admin"
$secret = Read-Host "Nouveau mot de passe (12 à 72 caractères ASCII)" -AsSecureString
$env:BOOTSTRAP_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new("", $secret).Password
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--app.bootstrap.reset-password=true"
```

Attendre le message **Mot de passe SUPER_ADMIN réinitialisé**. Arrêter ensuite le backend avec Ctrl+C, retirer les variables et redémarrer normalement :

```powershell
Remove-Item Env:BOOTSTRAP_ADMIN_EMAIL -ErrorAction SilentlyContinue
Remove-Item Env:BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:BOOTSTRAP_ADMIN_RESET_PASSWORD -ErrorAction SilentlyContinue
.\mvnw.cmd spring-boot:run
```

Le nouveau mot de passe est haché avec BCrypt. Les anciennes sessions et les anciens liens de réinitialisation deviennent invalides. L'option est désactivée par défaut et aucun endpoint public ne permet cette récupération locale.

## Direction commerciale, superviseurs et objectifs mensuels

La migration **V5** ajoute la hiérarchie, les zones, les réalisations et les synthèses sans effacer les comptes ni les rapports existants. Elle s'applique au démarrage du backend. Les anciens comptes SUPER_ADMIN restent inchangés.

### Première configuration depuis l'interface

1. Se connecter avec le SUPER_ADMIN existant. Dans **Commerciaux**, créer et activer le compte destiné au directeur (la création initiale lui donne le rôle commercial).
2. Dans **Organisation et zones**, sélectionner ce compte, choisir **Directeur commercial**, puis enregistrer. Seul le SUPER_ADMIN peut nommer un directeur. Le compte concerné doit se reconnecter après tout changement d'affectation ou de fonction.
3. Le directeur se connecte, crée / active les comptes nécessaires dans **Commerciaux**, puis nomme les **Superviseurs** dans **Organisation et zones** en renseignant leur équipe (AZO / Zehami) et leur zone.
4. Le directeur affecte chaque **Commercial** à un superviseur actif de la même équipe. Sans affectation, les brouillons restent possibles, mais l'envoi est bloqué.

Le superviseur consulte uniquement ses commerciaux et les rapports qui lui ont été envoyés. Le directeur consulte les rapports envoyés de toutes les équipes et leurs statistiques journalières, sans pouvoir modifier, supprimer ou examiner leur contenu. Les brouillons restent visibles uniquement par leur auteur et les administrateurs techniques. Le SUPER_ADMIN conserve la gestion technique et peut examiner les anciens rapports sans superviseur ; les rapports affectés sont examinés par leur superviseur uniquement.

### Objectifs par commercial et par mois

| Équipe | Catégorie | Objectif mensuel |
| --- | --- | ---: |
| AZO | Agences de flotte | 10 |
| AZO | Speeders indépendants | 45 |
| AZO | Clients | 200 |
| Zehami | Professionnels | 25 |
| Zehami | Utilisateurs | 200 |

Le tableau de bord propose le choix du mois, le réalisé validé, le pourcentage et le reste à atteindre pour chaque catégorie. Le mois repose sur la **journée du rapport** (fuseau métier Africa/Lagos), jamais sur sa date de création. Seuls les rapports **CONFIRMED** alimentent le réalisé ; les rapports soumis ou renvoyés sont signalés en attente. Un dépassement reste visible dans le réalisé, avec un reste à zéro et une barre plafonnée à 100 %.

Dans chaque rapport écrit ou statistique, renseigner **Réalisations du jour pour mes objectifs** : uniquement les nouvelles inscriptions de la journée. Ces catégories sont distinctes des profils AZO déjà disponibles : ne pas additionner les profils pour en déduire les speeders indépendants (les chauffeurs de flotte ne sont pas des indépendants). Les anciens rapports sans répartition ne sont pas inventés à zéro ; ils sont signalés comme incomplets dans le bilan. Le bilan d'un compte porte sur son équipe actuellement affectée. Les rapports des anciennes équipes restent consultables dans l'historique.

Un seul rapport envoyé par commercial, équipe et journée est autorisé afin d'éviter un double comptage ; réutiliser le rapport existant pour les corrections. Plusieurs brouillons restent possibles.

### Circuit des rapports

- Le commercial enregistre un brouillon puis **Envoie au superviseur**. Le superviseur et sa zone sont conservés avec le rapport ; une réaffectation ultérieure du commercial ne déplace pas les rapports déjà envoyés.
- Le superviseur peut **Renvoyer pour confirmation** : la rubrique exacte à corriger et la description de la correction sont obligatoires. Ces éléments figurent dans l'historique, dans la lecture et dans le PDF. Seul l'auteur corrige et soumet de nouveau.
- Le superviseur confirme les rapports, puis ouvre **Synthèses et rapport général**, choisit la journée et rédige son résumé. Tous les rapports déjà envoyés de cette journée / équipe doivent être confirmés ; au moins un est nécessaire.
- L'envoi de la synthèse est définitif pour cette journée et cette équipe. Les identifiants des rapports sources sont conservés, leurs compteurs restent verrouillés et les envois tardifs sont bloqués avec une explication. Vérifier les commerciaux sans rapport avant de clôturer. Une seule synthèse par superviseur, équipe et journée.
- Le directeur reçoit les synthèses dans **Rapport général**, avec un total distinct pour AZO et Zehami, la liste des superviseurs n'ayant pas transmis de synthèse, et les statistiques journalières par commercial. Chaque rapport source reste accessible en lecture et en PDF. La réception se fait dans l'application, sans email automatique.

Les changements de fonction d'un commercial avec des rapports en cours sont bloqués. Changer la fonction, l'équipe ou la zone d'un superviseur exige de réaffecter ses commerciaux si nécessaire et de transmettre les synthèses de ses rapports précédents.

### API

- GET `/api/sales/members`, PUT `/api/sales/members/{id}` : `{role,team,supervisorId,zone}`. Rôles `DIRECTEUR_COMMERCIAL`, `SUPERVISEUR`, `COMMERCIAL` ; la nomination d'un directeur reste réservée au SUPER_ADMIN. CSRF requis.
- GET `/api/sales/goals?team=AZO`, GET `/api/sales/objectives?month=2026-10` : définitions et bilan dans le périmètre du compte connecté.
- GET `/api/sales/summaries?date=2026-10-27`, POST `/api/sales/summaries` : `{date,content}`. Création réservée au superviseur, lecture par son auteur ou la direction.
- POST `/api/reports/{id}/review` : `{action,comment,location,version}` ; `location` et `comment` obligatoires pour `RETURNED`.
- Les rapports acceptent `goalCounts` (entiers 0..1 000 000), codes AZO `AGENCES_FLOTTE`, `SPEEDERS_INDEPENDANTS`, `CLIENTS` ; codes Zehami `PROFESSIONNELS`, `UTILISATEURS`.

L'API mobile peut aussi renvoyer ce champ optionnel, par exemple `"goalCounts":{"AGENCES_FLOTTE":2,"SPEEDERS_INDEPENDANTS":4,"CLIENTS":12}`. Lors d'un import, les valeurs du navigateur sont ignorées et seules les réalisations renvoyées par l'API sont conservées. Si ce champ manque, la répartition reste non fournie. Aucune connexion réelle à AZO ou Zehami n'est activée par cette mise à jour.
