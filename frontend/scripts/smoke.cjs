// Run only against an isolated QA database; this creates fictional test accounts and reports.
const { chromium, expect } = require("@playwright/test");
const assert = require("node:assert/strict");
(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_EXECUTABLE || undefined,
    args: ["--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu"],
  });
  const root = process.env.SMOKE_URL || "http://127.0.0.1:4200";
  const adminEmail = process.env.SMOKE_ADMIN_EMAIL,
    adminPassword = process.env.SMOKE_ADMIN_PASSWORD;
  if (!adminEmail || !adminPassword)
    throw Error(
      "Set SMOKE_ADMIN_EMAIL and SMOKE_ADMIN_PASSWORD for an isolated QA instance",
    );
  let crossOriginWrites = 0;
  const errors = [],
    tag = Date.now(),
    sellerEmail = `seller-${tag}@example.test`,
    supervisorEmail = `supervisor-${tag}@example.test`,
    directorEmail = `director-${tag}@example.test`,
    password = `qa-password-${tag}`;
  const admin = await browser.newPage({
      viewport: { width: 1440, height: 1000 },
    }),
    seller = await browser.newPage({ viewport: { width: 390, height: 844 } });
  const supervisor = await browser.newPage({
      viewport: { width: 390, height: 844 },
    }),
    director = await browser.newPage({
      viewport: { width: 1440, height: 1000 },
    });
  for (const page of [admin, seller, supervisor, director]) {
    page.on("pageerror", (e) => errors.push(e.message));
    if (process.env.SMOKE_CROSS_ORIGIN)
      page.on("request", (request) => {
        if (["POST", "PUT", "DELETE"].includes(request.method())) {
          assert.equal(new URL(request.url()).origin, "http://127.0.0.1:8080");
          assert.ok(
            request.headers()["x-xsrf-token"],
            "Missing cross-origin CSRF token",
          );
          crossOriginWrites++;
        }
      });
  }
  async function login(page, email, secret) {
    await page.goto(root + "/connexion");
    await page.getByLabel("E-mail", { exact: true }).fill(email);
    await page.getByLabel("Mot de passe", { exact: true }).fill(secret);
    await page
      .getByRole("button", { name: "Se connecter", exact: true })
      .click();
    await page.waitForURL("**/dashboard");
  }
  async function openLink(page, name) {
    const target = page.getByRole("link", { name, exact: true });
    const menu = page.getByRole("button", { name: "Menu", exact: true });
    if (!(await target.isVisible()) && (await menu.isVisible()))
      await menu.click();
    await target.click();
  }
  async function logoutPage(page) {
    const button = page.getByRole("button", {
      name: "Déconnexion",
      exact: true,
    });
    if (!(await button.isVisible()))
      await page.getByRole("button", { name: "Menu", exact: true }).click();
    await button.click();
  }
  async function noOverflow(page) {
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth > innerWidth,
      ),
      false,
      "Horizontal overflow",
    );
  }
  // Public self-registration is absent; old bookmarks redirect to login.
  await seller.goto(root + "/inscription");
  await seller.waitForURL("**/connexion");
  assert.equal(
    await seller
      .getByRole("link", { name: "Créer mon compte", exact: true })
      .count(),
    0,
  );
  await noOverflow(seller);
  await login(admin, adminEmail, adminPassword);
  await openLink(admin, "Commerciaux");
  await admin
    .getByText("Créer directement un commercial", { exact: true })
    .click();
  await admin.getByLabel("Prénom", { exact: true }).fill("Commercial");
  await admin.getByLabel("Nom", { exact: true }).fill("Test mobile");
  await admin.getByLabel("Email", { exact: true }).fill(sellerEmail);
  await admin.getByLabel("Mot de passe initial").fill(password);
  await admin.locator("#createTeam").selectOption("AZO");
  await admin
    .getByLabel("Code de parrainage mobile (facultatif)")
    .fill(`CODE-${tag}`);
  await admin
    .getByRole("button", { name: "Créer et activer le compte", exact: true })
    .click();
  await admin
    .getByRole("status")
    .filter({ hasText: "créé et activé" })
    .waitFor();
  async function createManager(page, first, email, role) {
    await openLink(page, "Organisation et zones");
    await page.getByText("Créer un responsable", { exact: true }).click();
    await page.getByLabel("Prénom du responsable", { exact: true }).fill(first);
    await page.getByLabel("Nom du responsable", { exact: true }).fill("Test");
    await page.getByLabel("Email du responsable", { exact: true }).fill(email);
    await page
      .getByLabel("Mot de passe du responsable", { exact: true })
      .fill(password);
    await page
      .getByLabel("Fonction du responsable", { exact: true })
      .selectOption(role);
    if (role === "SUPERVISEUR") {
      assert.equal(
        await page
          .locator('#managerRole option[value="DIRECTEUR_COMMERCIAL"]')
          .count(),
        0,
      );
      await page
        .getByLabel("Zone du nouveau superviseur", { exact: true })
        .fill("Cotonou Nord");
    }
    const previousViewport = page.viewportSize();
    await page.setViewportSize({ width: 390, height: 844 });
    await noOverflow(page);
    await page.screenshot({
      path: "/tmp/rapport-manager-mobile.png",
      fullPage: true,
    });
    await page.setViewportSize(previousViewport);
    await page
      .getByRole("button", { name: "Créer le responsable", exact: true })
      .click();
    await page
      .getByRole("status")
      .filter({ hasText: "créé et activé" })
      .waitFor();
    await noOverflow(page);
  }
  async function assign(page, email, role, supervisorId = null) {
    await openLink(page, "Organisation et zones");
    await page
      .locator("article")
      .filter({ hasText: email })
      .getByRole("button", { name: "Affecter / nommer", exact: true })
      .click();
    await page.getByLabel("Fonction", { exact: true }).selectOption(role);
    await page.getByLabel("Équipe", { exact: true }).selectOption("AZO");
    if (role === "SUPERVISEUR")
      await page.getByLabel("Zone du superviseur").fill("Cotonou Nord");
    if (supervisorId)
      await page
        .getByLabel("Superviseur responsable")
        .selectOption(supervisorId);
    await page
      .getByRole("button", { name: "Enregistrer l'affectation", exact: true })
      .click();
    await page
      .getByRole("status")
      .filter({ hasText: "Affectation enregistrée" })
      .waitFor();
  }
  await createManager(
    admin,
    "Direction",
    directorEmail,
    "DIRECTEUR_COMMERCIAL",
  );
  await login(director, directorEmail, password);
  await createManager(director, "Superviseur", supervisorEmail, "SUPERVISEUR");
  await director
    .locator("article")
    .filter({ hasText: sellerEmail })
    .getByRole("button", { name: "Affecter / nommer", exact: true })
    .click();
  await director
    .getByLabel("Superviseur responsable")
    .selectOption({ label: "Superviseur Test · Cotonou Nord" });
  await director
    .getByRole("button", { name: "Enregistrer l'affectation", exact: true })
    .click();
  await director
    .getByRole("status")
    .filter({ hasText: "Affectation enregistrée" })
    .waitFor();
  await login(supervisor, supervisorEmail, password);
  await login(seller, sellerEmail, password);
  await openLink(seller, "Rapports");
  await seller.getByRole("button", { name: "Rédiger un rapport" }).click();
  await seller.getByLabel("Titre du rapport").fill("Prospection mobile test");
  await seller
    .getByLabel("Déroulement de la journée")
    .fill("Visites à Cotonou. Plusieurs prospects intéressés.");
  await seller
    .getByRole("button", { name: "Ajouter une personne", exact: true })
    .click();
  await seller.locator("#azo-lastName-0").fill("Dossou");
  await seller.locator("#azo-firstName-0").fill("Élise");
  await seller.locator("#azo-profile-0").selectOption("CLIENT");
  await seller
    .getByRole("button", { name: "Ajouter une personne", exact: true })
    .click();
  await seller
    .getByRole("button", { name: "Retirer inscription 2", exact: true })
    .click();
  await seller
    .getByLabel("Enregistrement", { exact: true })
    .selectOption("SUBMITTED");
  await seller
    .getByRole("button", { name: "Enregistrer le rapport", exact: true })
    .click();
  await seller
    .getByRole("status")
    .filter({ hasText: "Rapport soumis" })
    .waitFor();
  await noOverflow(seller);
  await openLink(supervisor, "Rapports");
  let card = supervisor
    .locator(".report-card")
    .filter({ hasText: "Prospection mobile test" });
  await card
    .getByRole("button", { name: "Lire le rapport", exact: true })
    .click();
  await supervisor
    .getByLabel("Rubrique exacte à corriger (obligatoire pour un retour)")
    .fill("Réalisations du jour : Clients");
  await supervisor
    .getByLabel("Commentaire ou informations à confirmer")
    .fill("Confirmez les inscriptions attribuées.");
  await supervisor
    .getByRole("button", { name: "Renvoyer pour confirmation", exact: true })
    .click();
  await supervisor.getByRole("status").filter({ hasText: "renvoyé" }).waitFor();
  await seller.getByRole("button", { name: "Actualiser", exact: true }).click();
  card = seller
    .locator(".report-card")
    .filter({ hasText: "Prospection mobile test" });
  await expect(card).toContainText("À confirmer");
  await card
    .getByRole("button", { name: "Lire le rapport", exact: true })
    .click();
  await seller
    .getByRole("button", { name: "Corriger / compléter", exact: true })
    .click();
  await expect(seller.locator("#azo-firstName-0")).toHaveValue("Élise");
  await seller.locator("#azo-firstName-0").fill("Élise-Marie");
  await seller.locator("#azo-profile-0").selectOption("SPEEDER_MOTO_ESSENCE");
  await seller
    .getByRole("button", { name: "Ajouter une personne", exact: true })
    .click();
  await seller.locator("#azo-lastName-1").fill("Akindélé");
  await seller.locator("#azo-firstName-1").fill("Marc");
  await seller.locator("#azo-profile-1").selectOption("AGENCE_PRO");
  await seller
    .getByLabel("Comment souhaitez-vous faire votre bilan ?")
    .selectOption("STATS");
  await seller.getByLabel("Prospects contactés", { exact: true }).fill("12");
  await seller
    .getByLabel("Clients (objectif / mois : 200)", { exact: true })
    .fill("12");
  await seller
    .getByLabel("Agences de flotte (objectif / mois : 10)", { exact: true })
    .fill("2");
  await seller
    .getByLabel("Speeders indépendants (objectif / mois : 45)", { exact: true })
    .fill("4");
  await seller
    .getByLabel("Comptes inscrits (uniques)", { exact: true })
    .fill("3");
  await seller.getByLabel("Détailler les profils AZO", { exact: true }).check();
  await seller.getByLabel("Speeder moto à essence", { exact: true }).fill("2");
  await seller
    .getByLabel("Observations (facultatif)")
    .fill("Trois inscriptions attribuées, après vérification.");
  await seller
    .getByLabel("Enregistrement", { exact: true })
    .selectOption("SUBMITTED");
  await seller
    .getByRole("button", { name: "Enregistrer le rapport", exact: true })
    .click();
  await seller
    .getByRole("status")
    .filter({ hasText: "Rapport soumis" })
    .waitFor();
  await noOverflow(seller);
  await supervisor
    .getByRole("button", { name: "Actualiser", exact: true })
    .click();
  await supervisor
    .locator(".report-card")
    .filter({ hasText: "Prospection mobile test" })
    .getByRole("button", { name: "Lire le rapport", exact: true })
    .click();
  await supervisor
    .getByRole("button", { name: "Confirmer le rapport", exact: true })
    .click();
  await supervisor
    .getByRole("status")
    .filter({ hasText: "Rapport confirmé" })
    .waitFor();
  await seller.getByRole("button", { name: "Actualiser", exact: true }).click();
  await seller
    .locator(".report-card")
    .filter({ hasText: "Prospection mobile test" })
    .getByRole("button", { name: "Lire le rapport", exact: true })
    .click();
  await expect(seller.locator("#report-detail")).toContainText("Confirmé");
  await openLink(seller, "Tableau de bord");
  await expect(seller.locator("app-objectives")).toContainText(
    "188 à atteindre",
  );
  await expect(seller.locator("app-objectives")).toContainText(
    "41 à atteindre",
  );
  await expect(seller.locator("app-objectives")).toContainText("8 à atteindre");
  await openLink(supervisor, "Synthèses et rapport général");
  await supervisor
    .getByLabel("Résumé de la zone et points à signaler")
    .fill(
      "Résultats validés à Cotonou Nord : deux agences, quatre indépendants et douze clients.",
    );
  await supervisor
    .getByRole("button", {
      name: "Envoyer la synthèse au directeur",
      exact: true,
    })
    .click();
  await supervisor
    .getByRole("status")
    .filter({ hasText: "Synthèse transmise" })
    .waitFor();
  await noOverflow(supervisor);
  await openLink(director, "Synthèses et rapport général");
  await expect(director.locator("main")).toContainText(
    "Résultats validés à Cotonou Nord",
  );
  await director.getByRole("link", { name: /Lire le rapport #/ }).click();
  await expect(director.locator("#report-detail")).toContainText(
    "Prospection mobile test",
  );
  await expect(
    director.getByRole("button", { name: "Rédiger un rapport", exact: true }),
  ).toHaveCount(0);
  await expect(
    director.getByRole("button", { name: "Corriger / compléter", exact: true }),
  ).toHaveCount(0);
  await expect(
    director.getByRole("button", { name: "Confirmer le rapport", exact: true }),
  ).toHaveCount(0);
  await openLink(seller, "Rapports");
  await seller
    .locator(".report-card")
    .filter({ hasText: "Prospection mobile test" })
    .getByRole("button", { name: "Lire le rapport", exact: true })
    .click();
  const registrationDetail = seller.getByRole("region", {
    name: "Personnes inscrites sur AZO",
    exact: true,
  });
  await expect(registrationDetail).toContainText("Élise-Marie");
  await expect(registrationDetail).toContainText("Dossou");
  await expect(registrationDetail).toContainText("Speeder moto à essence");
  await expect(registrationDetail).toContainText("Akindélé");
  await expect(registrationDetail).toContainText("Agence PRO");
  const download = seller.waitForEvent("download");
  await seller
    .getByRole("button", { name: "Télécharger le PDF", exact: true })
    .click();
  assert.ok((await download).suggestedFilename().endsWith(".pdf"));
  for (const width of [360, 390, 768, 1440]) {
    await seller.setViewportSize({ width, height: 1000 });
    await noOverflow(seller);
  }
  if (process.env.QA_SCREENSHOT_DIR) {
    const fs = require("node:fs");
    fs.mkdirSync(process.env.QA_SCREENSHOT_DIR, { recursive: true });
    await seller.setViewportSize({ width: 390, height: 844 });
    await seller.screenshot({
      path: process.env.QA_SCREENSHOT_DIR + "/mobile.png",
      fullPage: true,
    });
    await admin.screenshot({
      path: process.env.QA_SCREENSHOT_DIR + "/desktop.png",
      fullPage: true,
    });
  }
  await openLink(admin, "Commerciaux");
  const sellerCard = admin.locator("article").filter({ hasText: sellerEmail });
  await sellerCard
    .getByText("Réinitialiser le mot de passe", { exact: true })
    .click();
  const freshPassword = `updated-${password}`;
  await sellerCard
    .getByLabel("Nouveau mot de passe (12 à 72 caractères)", { exact: true })
    .fill(freshPassword);
  await sellerCard
    .getByLabel("Confirmation", { exact: true })
    .fill(freshPassword);
  await sellerCard
    .getByRole("button", { name: "Changer le mot de passe", exact: true })
    .click();
  await admin.getByRole("status").filter({ hasText: "réinitialisé" }).waitFor();
  await seller.goto(root + "/rapports");
  await seller.waitForURL("**/connexion");
  await openLink(seller, "Mot de passe oublié ?");
  await seller.getByLabel("Email du compte", { exact: true }).fill(sellerEmail);
  await seller
    .getByRole("button", { name: "Recevoir le lien", exact: true })
    .click();
  await seller
    .getByRole("alert")
    .filter({ hasText: "pas encore configurée" })
    .waitFor();
  await login(seller, sellerEmail, freshPassword);
  await logoutPage(seller);
  await seller.waitForURL("**/connexion");
  await seller.goto(root + "/rapports");
  await seller.waitForURL("**/connexion");
  for (const page of [supervisor, director])
    for (const width of [360, 390, 768, 1440]) {
      await page.setViewportSize({ width, height: 1000 });
      await noOverflow(page);
    }
  // A second session receives a committed official announcement without refreshing its page.
  await openLink(supervisor, "Communiqués");
  await supervisor
    .getByText("Aucun communiqué pour le moment", { exact: true })
    .waitFor();
  await openLink(director, "Communiqués");
  await director.getByText("Publier un communiqué", { exact: true }).click();
  await director
    .getByLabel("Titre du communiqué", { exact: true })
    .fill("Réunion terrain " + tag);
  await director
    .getByLabel("Message officiel", { exact: true })
    .fill("Point de coordination à 09h. Merci aux équipes.");
  await director
    .getByRole("button", { name: "Diffuser le communiqué", exact: true })
    .click();
  await director
    .getByRole("status")
    .filter({ hasText: "Communiqué publié" })
    .waitFor();
  await supervisor
    .getByRole("heading", { name: "Réunion terrain " + tag, exact: true })
    .waitFor({ timeout: 15000 });
  assert.equal(
    await supervisor
      .getByText("Publier un communiqué", { exact: true })
      .count(),
    0,
  );
  await openLink(supervisor, "Notifications");
  await supervisor
    .getByText("Communiqué publié : Réunion terrain " + tag, { exact: true })
    .waitFor();
  await supervisor
    .getByRole("button", { name: "Tout marquer comme lu", exact: true })
    .click();
  await noOverflow(supervisor);
  await supervisor.setViewportSize({ width: 390, height: 844 });
  await noOverflow(supervisor);
  await supervisor.screenshot({
    path: "/tmp/rapport-notifications-mobile.png",
    fullPage: true,
  });
  // A zone grid updates a commercial's open dashboard through the existing live stream.
  await login(seller, sellerEmail, freshPassword);
  await openLink(director, "Grilles d’objectifs");
  await director
    .getByRole("button", { name: "Nouvelle grille", exact: true })
    .click();
  await director
    .getByLabel("Niveau de la zone", { exact: true })
    .selectOption("COMMUNE");
  await director
    .getByLabel("Nom de la zone", { exact: true })
    .fill("Cotonou Nord");
  await director
    .getByLabel("Effectif commercial prévu", { exact: true })
    .fill("5");
  await director.getByLabel("Objectif mensuel 1", { exact: true }).fill("12");
  await director
    .getByRole("button", { name: "Enregistrer la grille", exact: true })
    .click();
  await director
    .getByRole("status")
    .filter({ hasText: "Grille enregistrée" })
    .waitFor();
  await expect(
    seller.locator("app-objectives").getByText(/\d+ \/ 12/),
  ).toBeVisible({ timeout: 15000 });
  const zoneCard = director.locator("article").filter({
    has: director.getByRole("heading", { name: "Cotonou Nord", exact: true }),
  });
  await zoneCard
    .getByRole("button", { name: "Modifier la grille", exact: true })
    .click();
  await director.getByLabel("Objectif mensuel 1", { exact: true }).fill("15");
  await director
    .getByRole("button", { name: "Enregistrer la grille", exact: true })
    .click();
  await expect(
    seller.locator("app-objectives").getByText(/\d+ \/ 15/),
  ).toBeVisible({ timeout: 15000 });
  await director.setViewportSize({ width: 390, height: 844 });
  await noOverflow(director);
  await director.screenshot({
    path: "/tmp/rapport-zone-grid-mobile.png",
    fullPage: true,
  });
  await director.setViewportSize({ width: 1440, height: 1000 });
  // A removed custom criterion remains editable on an older saved draft.
  await zoneCard
    .getByRole("button", { name: "Modifier la grille", exact: true })
    .click();
  await director
    .getByRole("button", { name: "Ajouter un critère", exact: true })
    .click();
  await director
    .getByLabel("Libellé du critère 4", { exact: true })
    .fill("Démonstrations boutiques");
  await director.getByLabel("Objectif mensuel 4", { exact: true }).fill("7");
  await director
    .getByRole("button", { name: "Enregistrer la grille", exact: true })
    .click();
  await director
    .getByRole("status")
    .filter({ hasText: "Grille enregistrée" })
    .waitFor();
  await openLink(seller, "Rapports");
  await seller
    .getByRole("button", { name: "Rédiger un rapport", exact: true })
    .click();
  const draftTitle = "Brouillon critère historique " + tag;
  await seller.getByLabel("Titre du rapport", { exact: true }).fill(draftTitle);
  await seller
    .getByLabel("Comment souhaitez-vous faire votre bilan ?")
    .selectOption("STATS");
  await seller
    .getByLabel("Démonstrations boutiques (objectif / mois : 7)", {
      exact: true,
    })
    .fill("3");
  await seller
    .getByRole("button", { name: "Enregistrer le rapport", exact: true })
    .click();
  await seller
    .getByRole("status")
    .filter({ hasText: "Brouillon enregistré" })
    .waitFor();
  await zoneCard
    .getByRole("button", { name: "Modifier la grille", exact: true })
    .click();
  await director
    .getByRole("button", { name: "Retirer le critère 4", exact: true })
    .click();
  await director
    .getByRole("button", { name: "Enregistrer la grille", exact: true })
    .click();
  await director
    .getByRole("status")
    .filter({ hasText: "Grille enregistrée" })
    .waitFor();
  await seller.goto(root + "/rapports");
  await seller
    .locator(".report-card")
    .filter({ hasText: draftTitle })
    .getByRole("button", { name: "Modifier", exact: true })
    .click();
  await expect(
    seller.getByLabel("Démonstrations boutiques (objectif / mois : 0)", {
      exact: true,
    }),
  ).toHaveValue("3");
  await seller
    .getByRole("button", { name: "Enregistrer le rapport", exact: true })
    .click();
  await seller
    .getByRole("status")
    .filter({ hasText: "Brouillon enregistré" })
    .waitFor();
  // Disabling a commercial invalidates the existing session, without deleting reports.
  await openLink(director, "Organisation et zones");
  const sellerMember = director
    .locator("article")
    .filter({ hasText: sellerEmail });
  await sellerMember
    .getByRole("button", { name: "Désactiver le compte", exact: true })
    .click();
  await director
    .getByRole("button", { name: "Confirmer la désactivation", exact: true })
    .click();
  await seller.waitForURL("**/connexion", { timeout: 15000 });
  await sellerMember
    .getByRole("button", { name: "Réactiver le compte", exact: true })
    .click();
  await director
    .getByRole("button", { name: "Confirmer la réactivation", exact: true })
    .click();
  await director
    .getByRole("status")
    .filter({ hasText: "Compte réactivé" })
    .waitFor();
  await login(seller, sellerEmail, freshPassword);
  // A supervisor with dependants stays active and gets an explicit explanation.
  const supervisorMember = director
    .locator("article")
    .filter({ hasText: supervisorEmail });
  await supervisorMember
    .getByRole("button", { name: "Désactiver le compte", exact: true })
    .click();
  await director
    .getByRole("button", { name: "Confirmer la désactivation", exact: true })
    .click();
  await director
    .getByRole("alert")
    .filter({ hasText: /Réaffectez/ })
    .waitFor();
  // The root replaces a country director atomically from the organization screen.
  const replacementEmail = `replacement-${tag}@example.test`;
  await openLink(admin, "Commerciaux");
  const createDetails = admin.locator("details").filter({
    has: admin.getByText("Créer directement un commercial", { exact: true }),
  });
  if (!(await createDetails.evaluate((el) => el.open)))
    await admin
      .getByText("Créer directement un commercial", { exact: true })
      .click();
  await admin.getByLabel("Prénom", { exact: true }).fill("Direction");
  await admin.getByLabel("Nom", { exact: true }).fill("Relève");
  await admin.getByLabel("Email", { exact: true }).fill(replacementEmail);
  await admin.getByLabel("Mot de passe initial").fill(password);
  await admin
    .getByRole("button", { name: "Créer et activer le compte", exact: true })
    .click();
  await admin
    .getByRole("status")
    .filter({ hasText: "créé et activé" })
    .waitFor();
  await openLink(admin, "Organisation et zones");
  await admin
    .locator("article")
    .filter({ hasText: directorEmail })
    .getByRole("button", { name: "Remplacer le directeur", exact: true })
    .click();
  const candidateOption = await admin
    .locator("#directorCandidate option")
    .filter({ hasText: replacementEmail })
    .textContent();
  await admin
    .getByLabel("Nouveau directeur", { exact: true })
    .selectOption({ label: candidateOption.trim() });
  await admin
    .getByRole("button", { name: "Nommer le remplaçant", exact: true })
    .click();
  await admin
    .getByRole("status")
    .filter({ hasText: "est le nouveau directeur" })
    .waitFor();
  await director.waitForURL("**/connexion", { timeout: 15000 });
  await login(director, replacementEmail, password);
  await openLink(director, "Organisation et zones");
  await director
    .getByRole("heading", { name: "Direction Relève", exact: true })
    .waitFor();
  assert.deepEqual(errors, []);
  console.log(
    "PASS: live zone objectives, safe deactivation/reactivation, guarded supervisor disabling and atomic director replacement, live announcement and notification between two sessions without reload, direct creation of director by root and supervisor by director, supervisor zone, commercial assignment, scoped routing, precise correction, monthly goals, supervisor summary, director read-only general report, admin-only commercial creation, login, written report, return, stats report, resubmission, confirmation, AZO profile breakdown, named AZO registrations with add/remove/edit and persisted profiles, PDF download, admin password reset, stale session invalidation, forgot-password form, logout, 360/390/768/1440px without overflow; no browser errors.",
  );
  if (process.env.SMOKE_CROSS_ORIGIN) assert.ok(crossOriginWrites > 10);
  await browser.close();
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
