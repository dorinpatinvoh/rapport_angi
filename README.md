# Rapports terrain - ANGI DSN PRO SARL

Angular 20 + Spring Boot 4 / Java 21 : comptes commerciaux AZO et Zehami, rapports quotidiens et suivi par le super administrateur. Interface adaptée au téléphone, à la tablette et à l'ordinateur, avec les couleurs et les logos fournis par ANGI.

## Comptes et accès

- Le premier SUPER_ADMIN se crée par variables d'environnement (voir le démarrage ci-dessous). Un compte existant n'est pas recréé.
- L’auto-inscription est supprimée. `/inscription` redirige vers la connexion et `/api/auth/register` est refusé, y compris avec un ancien frontend.
- Seuls le SUPER_ADMIN et le DIRECTEUR_COMMERCIAL créent et activent les commerciaux dans leur périmètre. Les anciens comptes inactifs peuvent toujours être activés par la direction.
- Pour créer directement un commercial, ouvrir **Commerciaux > Créer directement un commercial**, remplir les champs et cliquer **Créer et activer le compte**. Transmettre personnellement l'email et le mot de passe initial au commercial. Aucun email automatique n'est envoyé.
- Un code de parrainage doit être unique dans son équipe. Il doit déjà exister dans l'application AZO ou Zehami : cette application ne crée pas de comptes dans les applications mobiles.
- Les mots de passe sont stockés avec BCrypt. Aucun compte ne peut être créé depuis une route publique.

## Rapport sur mobile

1. Se connecter, ouvrir **Rapports > Rédiger un rapport**.
2. Choisir la journée et le mode : compte rendu écrit, ou statistiques avec observations facultatives.
3. En mode écrit, raconter les zones visitées, les besoins, les résultats, les difficultés et les prochaines actions.
4. En mode statistiques, saisir les prospects contactés et les inscriptions attribuées. Sans API, les chiffres sont explicitement déclaratifs. Si l'API de l'équipe est configurée, cocher l'import ; les chiffres sont récupérés de nouveau côté serveur à l'enregistrement.
5. Enregistrer en brouillon ou soumettre au superviseur affecté. Après soumission, le contenu est verrouillé.
6. Le superviseur affecté ouvre **Lire le rapport**, puis confirme le rapport ou utilise **Renvoyer pour confirmation**, avec une rubrique et un commentaire obligatoires. Le commercial consulte le commentaire, corrige/complète et soumet à nouveau. L'historique des retours est conservé.
7. Le bouton **PDF** télécharge un document avec le logo ANGI, celui de l'équipe, le nom du commercial, la journée, les chiffres et leur origine, les observations et le suivi. Les longues descriptions passent sur plusieurs pages.

Un commercial ne voit que ses rapports. Le directeur consulte les rapports soumis de son pays et de ses projets ; le superviseur consulte ceux qui lui sont affectés. Le SUPER_ADMIN conserve la vue globale. Le contrôle des anciens rapports sans superviseur reste réservé au SUPER_ADMIN. Seul l'auteur peut modifier son contenu ; seuls les brouillons sont supprimables. Un super administrateur ne peut pas confirmer ses propres rapports. Les conflits entre deux versions ouvertes simultanément sont refusés plutôt que d'écraser silencieusement une modification.

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
| POST | `/api/auth/register` | Supprimé : accès refusé |
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

Les tests couvrent sessions, CSRF, refus de l’auto-inscription, activation par la direction, permissions, unicité des codes, transitions et conflits de version, origine des statistiques, contrat API mobile, accès et contenu des PDF et pagination. Les migrations sont testées sur H2 en mode PostgreSQL et le conteneur est démarré sur PostgreSQL en CI.

Sur Linux/CI avec Java 21, un test navigateur démarre automatiquement une base H2 isolée et teste l'interface réelle :

```bash
cd frontend
npm ci
npm run build
npx playwright install --with-deps chromium
npm run test:smoke
```

Ce test crée uniquement des comptes fictifs dans une base H2 en mémoire ; il n'utilise pas votre PostgreSQL. Les ports 8080 et 4200 doivent être libres. Le parcours couvre refus de l’auto-inscription, création commerciale par la direction, rapport écrit sur mobile, retour, bilan chiffré, nouvelle soumission, confirmation, PDF et déconnexion, avec vérification des largeurs 360/390/768/1440 px.

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

## Personnes inscrites dans les rapports AZO

Dans un rapport **AZO**, écrit ou statistique, la rubrique **Personnes inscrites sur AZO** permet d'ajouter plusieurs lignes avec **nom, prénom et profil d'inscription**. Les profils proposés sont ceux du catalogue AZO déjà utilisé pour les compteurs. Une ligne correspond à une inscription à un profil ; une personne ayant plusieurs profils peut figurer sur plusieurs lignes. Les homonymes ne sont pas bloqués.

Le commercial peut ajouter, corriger ou retirer une ligne tant que son rapport est en brouillon ou renvoyé pour correction. La liste est conservée à l'envoi et apparaît dans la lecture du rapport et son PDF, avec pagination si nécessaire. Les droits existants s'appliquent également aux noms : le superviseur affecté et la direction peuvent consulter le rapport, les autres commerciaux n'y ont pas accès.

Cette liste est déclarée par le commercial, y compris lorsqu'il importe ses compteurs depuis une API mobile. Elle ne modifie pas automatiquement les compteurs de profils, le nombre de comptes uniques ou les objectifs mensuels. Les anciens rapports restent sans liste nominative et indiquent **Liste nominative non fournie**. Zehami ne propose pas cette rubrique.

La migration **V6** crée la table associée sans modifier les données existantes. Les API de création/modification acceptent un champ optionnel `azoRegistrations` :

```json
{
  "azoRegistrations": [
    { "lastName": "Dossou", "firstName": "Élise", "profileCode": "CLIENT" },
    { "lastName": "Akindélé", "firstName": "Marc", "profileCode": "AGENCE_PRO" }
  ]
}
```

Chaque ligne exige un nom et un prénom non vides (100 caractères maximum chacun) et un code de profil AZO connu. Maximum : 500 lignes par rapport. Une liste non vide sur un rapport Zehami est rejetée. Lors d'une modification, l'absence du champ conserve la liste existante pour rester compatible avec les anciens clients API ; `[]` vide explicitement la liste.

## Backend sur Render avec Docker

Le backend Java 21 dispose de `backend/Dockerfile` et d'un contexte Docker limité à `backend`. L'image finale utilise un JRE et un utilisateur sans privilèges. Elle exécute le JAR ; aucun serveur de développement ni PostgreSQL local n'est lancé dans le conteneur. Le frontend LWS se prépare séparément : cette étape fournit uniquement le backend.

Dans **New Web Service**, sélectionner le dépôt `dorinpatinvoh/rapport_angi`, puis :

| Champ | Valeur |
| --- | --- |
| Name | `rapport-angi-backend` |
| Language | `Docker` |
| Branch | `main` |
| Root Directory | `backend` |
| Dockerfile Path | `Dockerfile` (relatif à Root Directory) |
| Docker Build Context | `.` si ce champ est proposé |
| Docker Command | laisser vide (utilise ENTRYPOINT) |
| Instance | `Free` pour un essai |

Les champs Build Command / Start Command de Node ne sont pas utilisés en mode Docker. Render fournit `PORT` ; Spring écoute déjà `${PORT:8080}` sur toutes les interfaces par défaut. Conserver le contrôle de santé TCP par défaut à cette étape.

Avant de lancer le service, configurer une base PostgreSQL dédiée aux rapports, puis les variables Render :

- `DB_URL` : URL JDBC, par exemple `jdbc:postgresql://HOTE:5432/BASE?sslmode=require` pour un fournisseur exigeant TLS. Une URL commençant par `postgresql://` doit être adaptée au format JDBC ; renseigner séparément les identifiants ci-dessous.
- `DB_USERNAME` et `DB_PASSWORD` : identifiants de cette base.
- `APP_PUBLIC_URL=https://rapports.angidsnpro.com` : adresse du frontend, utilisée notamment pour les liens de réinitialisation.
- `SESSION_COOKIE_SECURE=true` : cookies de session transmis uniquement par HTTPS.
- `SERVER_FORWARD_HEADERS_STRATEGY=framework` : prise en compte du proxy HTTPS de Render.
- Au premier démarrage d'une **nouvelle base**, `BOOTSTRAP_ADMIN_EMAIL` et `BOOTSTRAP_ADMIN_PASSWORD` créent le SUPER_ADMIN. Fournir son propre secret d'au moins 12 caractères, limité à 72 octets UTF-8. Retirer ces deux variables après la création du compte, puis redéployer. Ne pas activer `BOOTSTRAP_ADMIN_RESET_PASSWORD` pour une création normale.

La nouvelle base sera initialisée par Flyway. Les données de la base locale ne sont pas transférées automatiquement. Ne pas pointer ce service vers la base métier d'AZO. Si des comptes ou rapports locaux doivent être conservés, préparer leur transfert avant de démarrer les saisies en ligne.

Les secrets se saisissent dans Render, jamais dans Git, le Dockerfile ou une capture partagée. Les variables SMTP existantes restent optionnelles ; sur Render Free, les ports SMTP usuels sont bloqués, donc l'envoi des liens email nécessite une adaptation à un service d'email via HTTPS ou un hébergement adapté. L'application conserve la récupération locale de l'administrateur et l'assistance du SUPER_ADMIN pour les commerciaux.

L'offre gratuite Render peut se mettre en veille. Utiliser une base durable dédiée (par exemple la base Neon déjà migrée) et vérifier les quotas de l'offre choisie.

La CI construit l'image et vérifie son démarrage dans une limite de 512 Mo avec une base PostgreSQL 16 isolée, les migrations, le cookie CSRF et une connexion SUPER_ADMIN de test.


## Frontend LWS et API Render sur deux sous-domaines

Le frontend se déploie sur `https://rapports.angidsnpro.com`, et l'API sur `https://api-rapports.angidsnpro.com`. Les deux domaines doivent avoir un certificat HTTPS valide.

Sur Render, conserver `APP_PUBLIC_URL=https://rapports.angidsnpro.com`, `SESSION_COOKIE_SECURE=true` et `SERVER_FORWARD_HEADERS_STRATEGY=framework`. L'origine autorisée CORS reprend `APP_PUBLIC_URL` par défaut. `APP_ALLOWED_ORIGINS` permet de fournir une liste d'origines exactes séparées par des virgules ; aucun joker. Ne pas autoriser l'ensemble des sous-domaines. Aucun changement de base ni nouvelle importation n'est nécessaire.

Le frontend transmet les cookies uniquement vers son API et récupère le jeton CSRF depuis la réponse JSON `/api/auth/csrf`. Le jeton reste en mémoire, est renouvelé après connexion, et accompagne les écritures dans `X-XSRF-TOKEN`. Le backend garde la protection CSRF et autorise les requêtes CORS avec cookies depuis l'origine configurée. Les cookies restent propres à l'hôte API, sans domaine parent partagé.

Depuis PowerShell :

```powershell
cd D:\rapport
git pull origin main
cd frontend
npm ci
npm run build:lws
```

Avec FileZilla, envoyer **le contenu** de `D:\rapport\frontend\dist\frontend\browser` dans `/rapports.angidsnpro.com/` : `index.html`, les fichiers JS/CSS et `.htaccess`, directement dans ce dossier. Ne pas envoyer le dossier `browser` lui-même, `node_modules`, les sources ou le backend. Conserver une copie locale de `default_index.html`, puis retirer cette page d'attente de ce seul dossier pour qu'elle n'interfère pas avec l'application. Ne pas modifier les autres sites du compte. `.htaccess` fournit le retour vers `index.html` pour les routes Angular ; penser à transférer les fichiers cachés. Le dossier local `dist` est généré et n'est pas versionné.

Ouvrir ensuite `/connexion`, se connecter avec un compte existant, recharger `/rapports` et essayer la lecture d'un rapport et son PDF. La construction locale normale `npm run build` et `npm start` continuent d'utiliser `/api` et le proxy de développement. La configuration `cross-origin` est réservée à la QA avec une base H2 isolée, jamais à LWS.

La CI vérifie le même parcours complet dans un vrai navigateur avec deux origines différentes, sans proxy API, puis fournit l'artefact `frontend-lws` contenant les fichiers prêts à transférer.


## Création directe de l'encadrement et email entreprise

Depuis **Organisation et zones → Créer un responsable**, le SUPER_ADMIN peut créer un directeur commercial ou un superviseur. Le directeur peut créer un superviseur et sa zone, puis affecter les commerciaux. Les commerciaux se créent toujours dans **Commerciaux → Créer directement un commercial**. Les comptes sont actifs à leur création et utilisent un email et un mot de passe initial ; les mots de passe ne sont jamais renvoyés dans les réponses API. Un directeur ne peut ni créer ni modifier un autre directeur, ni créer un administrateur. La création du compte et l'affectation du rôle sont transactionnelles.

`POST /api/sales/members` reçoit `{ "account": { "firstName": "…", "lastName": "…", "email": "…", "password": "…", "team": "AZO" }, "role": "DIRECTEUR_COMMERCIAL", "zone": "" }`. Pour un superviseur, `role` vaut `SUPERVISEUR` et la zone non vide est obligatoire.

La migration Java V7 remplace l'email de l'unique SUPER_ADMIN existant par `angidsnpro@gmail.com`, en conservant son identifiant, son état et son mot de passe. Elle invalide ses anciennes sessions et ses liens de réinitialisation. Elle ne crée aucun compte et n'accorde aucun rôle. Si plusieurs SUPER_ADMIN existent ou si l'adresse appartient déjà à un autre compte, elle ne choisit pas arbitrairement et affiche un avertissement ; vérifier alors le compte dans Neon. Sur une base neuve sans SUPER_ADMIN, utiliser le bootstrap habituel avec l'email entreprise.

**Récupération Render en cours :** avant le redéploiement de cette version, renseigner `BOOTSTRAP_ADMIN_EMAIL=angidsnpro@gmail.com` si la migration doit renommer l'unique SUPER_ADMIN, conserver le mot de passe choisi dans `BOOTSTRAP_ADMIN_PASSWORD` et `BOOTSTRAP_ADMIN_RESET_PASSWORD=true`. Ne pas créer un autre compte pour contourner une ambiguïté de migration. Après une connexion réussie, supprimer les trois variables ensemble puis redéployer. Le changement d'email ne définit aucun mot de passe public.

Après un `git pull origin main`, reconstruire le frontend avec `npm ci` puis `npm run build:lws` et transférer à nouveau le contenu de `frontend/dist/frontend/browser/` dans le dossier LWS du sous-domaine.


## Organisation par pays, notifications et communiqués (V8)

L’application conserve sa pile **Angular 20 / Spring Boot 4 / Java 21 / PostgreSQL**. Aucun changement vers React/Vite ou Node n’est nécessaire pour les fonctionnalités demandées.

- **Un directeur par pays** : le champ réservé au directeur a un index unique en base et les nominations verrouillent la ligne du pays. Un remplacement se fait désormais depuis **Organisation et zones → Remplacer le directeur** ; le compte précédent est désactivé et libère sa place dans la même transaction. Le pays utilise un code ISO à deux lettres.
- **Plusieurs projets** : le super administrateur coche AZO et/ou ZEHAMI pour chaque directeur. Le directeur peut gérer les superviseurs et commerciaux uniquement dans ce pays et ces projets. Le super administrateur possède les mêmes fonctions sans restriction de périmètre.
- **Zones** : sélectionner Département ou Commune, puis saisir le nom géographique. Le directeur rattache directement les commerciaux à un superviseur actif de la même combinaison pays/projet. Les changements de périmètre sont bloqués tant que les rapports concernés ne sont pas terminés.
- **Communication** : la rubrique Communiqués est accessible à tous les comptes connectés. La direction publie dans son pays et ses projets ; le super administrateur peut choisir un pays ou tous les pays, et un ou plusieurs projets ou tous. Les utilisateurs consultent uniquement les annonces qui les concernent. Les textes sont affichés comme texte, sans HTML exécuté.
- **Notifications** : le journal durable trace créations, affectations, changements de statut, réinitialisations commerciales par le super administrateur, ajouts de pays et publications. L’acteur et la date sont visibles. Les données sensibles et mots de passe ne sont jamais inclus. Le journal est filtré selon le pays/projet de chaque lecteur.
- **Temps réel** : un flux SSE avec cookies transmet uniquement un signal d’actualisation après validation de la transaction. Le frontend recharge ensuite les données par les endpoints REST autorisés. Le flux expire après 25 secondes et se reconnecte ; un heartbeat est émis toutes les 15 secondes. La fermeture de session ferme le flux côté frontend. Les échecs de transaction ne génèrent pas de fausses notifications.

### Migration des comptes existants

Faire une sauvegarde avant déploiement. Flyway ajoute V8 sans supprimer les comptes ni les rapports. Les données existantes sont rattachées à BJ (Bénin) et les rapports conservent ensuite leur pays historique. Si un seul directeur existe, il reçoit BJ et les deux projets existants. Si plusieurs directeurs existent déjà, leur pays reste à configurer par le SUPER_ADMIN dans Organisation et zones ; ils ne reçoivent pas arbitrairement un pays et leur accès de direction reste restreint jusqu’à cette affectation. Les anciennes zones conservent leur texte ; leur niveau doit être précisé à la prochaine modification.

### API et validation

- GET/POST `/api/sales/countries` : catalogue des pays ; ajout réservé au SUPER_ADMIN.
- POST `/api/sales/members` : `{account:{firstName,lastName,email,password,team,country},role,zone,zoneLevel,projects}`.
- PUT `/api/sales/members/{id}` : `{role,team,supervisorId,zone,zoneLevel,country,projects}`. `zoneLevel` vaut `DEPARTMENT` ou `COMMUNE`. Un directeur exige au moins un projet valide.
- GET `/api/communications/announcements` et `/notifications` : 50 derniers éléments visibles.
- POST `/api/communications/announcements` : `{title,content,country,projects}` ; `country:null` et `projects:[]` désignent une diffusion globale autorisée uniquement au SUPER_ADMIN.
- GET `/api/communications/stream` : SSE authentifié, sans secret dans l’URL, autorisation des données vérifiée sur chaque requête REST.

Les tests couvrent les accès entre pays/projets, l’unicité du directeur jusque dans la base, les rattachements géographiques et le ciblage des annonces/notifications. Le test navigateur utilise deux sessions pour vérifier l’arrivée d’un communiqué sans rechargement et reprend le parcours complet des rapports sur mobile et ordinateur.

### Mise en ligne de la nouvelle interface

Le backend Render reçoit les migrations lors du déploiement de main. Pour LWS : `git pull origin main`, puis dans `frontend`, `npm ci` et `npm run build:lws`. Envoyer tout le contenu de `dist/frontend/browser/`, y compris `.htaccess`, dans `/rapports.angidsnpro.com/`. Le build LWS utilise désormais des noms de fichiers avec empreinte pour éviter l’affichage d’un ancien JavaScript après une mise à jour. Conserver les fichiers de l’ancienne version jusqu’à la fin du transfert pour les sessions déjà ouvertes, puis actualiser la page.


## Administration autonome des grilles et des comptes (V9)

### Grilles par département et commune

La rubrique **Grilles d’objectifs**, accessible au SUPER_ADMIN et au DIRECTEUR_COMMERCIAL, permet de créer une zone par pays/projet/niveau, puis d’enregistrer ses critères et objectifs mensuels. Les pays, projets et permissions restent ceux de V8. Aucune valeur issue d’une image non reçue n’est inventée ni préchargée.

- Chaque ligne contient un libellé et une cible **mensuelle par commercial**. On peut ajouter jusqu’à 30 critères, y compris de nouveaux critères, sans modifier le code. L’identifiant de suivi est généré lors de la création et conservé lors d’un changement de libellé.
- L’effectif commercial prévu est un indicateur de planification (0 à 10 000), pas un blocage d’affectation. Le total prévu est `cible individuelle × effectif prévu`. L’écran affiche aussi l’effectif actif réellement rattaché à la zone ; un département inclut ses communes.
- Une commune peut définir sa grille ou hériter de celle d’un département du même pays/projet. Les cibles héritées changent dès la mise à jour du département. Un objectif individuel à zéro signifie aucun minimum et ne provoque pas de division par zéro.
- Le pays, le projet, le niveau et le nom d’une zone créée sont fixes. Pour changer ce périmètre, créer une autre zone puis réaffecter le superviseur. Les critères, les cibles, l’effectif prévu et le département parent d’une commune restent modifiables.
- Dans **Organisation et zones**, sélectionner la zone configurée lors de la création ou de l’affectation d’un superviseur. Ses commerciaux reçoivent automatiquement la grille, sans copier les objectifs dans chaque compte. Les zones libres existantes gardent la grille standard tant qu’aucune grille correspondante n’est définie. Une grille créée avec le même nom/pays/projet/niveau rattache les superviseurs existants correspondants.
- Les objectifs consultés utilisent la grille actuelle, y compris pour un mois précédent sélectionné. Les réalisations des rapports confirmés ne sont pas réécrites. Les libellés enregistrés dans les rapports et leurs PDF sont conservés ; les synthèses peuvent afficher les critères personnalisés. Les statistiques mobiles absentes pour un nouveau critère ne sont pas fabriquées.
- Les modifications sont annoncées via le journal et le flux en direct. Les tableaux d’objectifs se recalculent sans rechargement. Un changement de superviseur/zone au sein du même pays/projet ne déconnecte pas le commercial ; un changement de droits, de pays ou de projet continue d’invalider sa session.

### Désactivation et remplacement

**Organisation et zones → Désactiver le compte / Réactiver le compte** fonctionne pour les commerciaux et superviseurs dans le périmètre du gestionnaire. La confirmation précise l’action. La désactivation invalide les sessions sans supprimer le compte, les rapports ni le journal. Un superviseur doit d’abord réaffecter tous ses commerciaux et terminer ses validations/synthèses. Les comptes privilégiés et le compte de l’acteur sont protégés.

**Remplacer le directeur** est réservé au SUPER_ADMIN. Choisir un compte du même pays, puis confirmer la nomination. Les projets du pays sont transférés au nouveau directeur, l’ancien compte est conservé mais désactivé et sa place de directeur est libérée. Un ancien directeur désactivé peut être choisi pour une nouvelle relève dans son pays. Le candidat doit avoir terminé ses rapports et, s’il est superviseur, réaffecté ses commerciaux et terminé ses synthèses. Toute erreur annule l’ensemble du remplacement : l’ancien directeur conserve sa place et ses accès. Le pays est verrouillé pendant l’opération et l’index unique reste appliqué. Une demande reposant sur un ancien directeur déjà remplacé est refusée.

### API et stockage

- `GET/POST /api/sales/zones`, `PUT /api/sales/zones/{id}` : grilles de la direction, avec `country`, `project`, `level`, `name`, `parentId`, `plannedStaff`, `inheritGoals`, `goals:[{code,label,target}]` et `version` à la modification. Une version périmée est refusée pour éviter d’écraser une autre modification.
- `zoneConfigId` peut être fourni lors de la création et de l’affectation d’un superviseur ; le serveur vérifie le pays et le projet et reprend le nom/niveau de cette zone.
- `PUT /api/sales/members/{id}/status` : `{enabled,revision}` ; la révision vient du membre renvoyé par l’API.
- `POST /api/sales/directors/replace` : `{country,expectedDirectorId,candidateId}` ; réservé au SUPER_ADMIN.
- V9 ajoute `goal_zones`, `zone_goal_grids`, `zone_goal_metrics`, le lien de zone sur `users` et les libellés d’objectifs des rapports dans `report_goal_labels`. Les comptes et données existants sont conservés.

Les tests couvrent les changements immédiats de cibles, l’héritage et la surcharge par commune, les libellés historiques/PDF, les cibles nulles, les conflits de version, les périmètres de pays/projet, les désactivations protégées, le remplacement et son annulation complète en cas de refus. Le parcours navigateur vérifie l’actualisation d’un tableau ouvert, la fermeture de session à la désactivation, la réactivation et la connexion du directeur remplaçant.

Après récupération de `main`, reconstruire avec `npm run build:lws`, puis transférer le contenu de `frontend/dist/frontend/browser/` sur LWS. Le backend applique V9 au déploiement Render ; la nouvelle interface nécessite cette version backend.
