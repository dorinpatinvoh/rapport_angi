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
    pendingEmail = `pending-${tag}@example.test`,
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
  async function noOverflow(page) {
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth > innerWidth,
      ),
      false,
      "Horizontal overflow",
    );
  }
  // Public registration is visible and remains pending until an administrator activates it.
  await seller.goto(root + "/connexion");
  await seller
    .getByRole("link", { name: "Créer mon compte", exact: true })
    .click();
  await seller.getByLabel("Prénom", { exact: true }).fill("Demande");
  await seller.getByLabel("Nom", { exact: true }).fill("Test");
  await seller.getByLabel("Email", { exact: true }).fill(pendingEmail);
  await seller.getByLabel("Mot de passe", { exact: true }).fill(password);
  await seller.getByLabel("Équipe", { exact: true }).selectOption("ZEHAMI");
  await seller
    .getByRole("button", { name: "Demander mon inscription" })
    .click();
  await seller.getByRole("status").filter({ hasText: "activé" }).waitFor();
  await noOverflow(seller);
  await login(admin, adminEmail, adminPassword);
  await admin.getByRole("link", { name: "Commerciaux", exact: true }).click();
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
  const pendingCard = admin
    .locator("article")
    .filter({ hasText: pendingEmail });
  await pendingCard
    .getByRole("button", { name: "Activer le compte", exact: true })
    .click();
  await admin.getByRole("status").filter({ hasText: "mis à jour" }).waitFor();
  async function createAccount(page, first, last, email) {
    await page.getByRole("link", { name: "Commerciaux", exact: true }).click();
    const details = page.locator("details").filter({
      has: page.getByText("Créer directement un commercial", { exact: true }),
    });
    if (!(await details.evaluate((el) => el.open)))
      await page
        .getByText("Créer directement un commercial", { exact: true })
        .click();
    await page.getByLabel("Prénom", { exact: true }).fill(first);
    await page.getByLabel("Nom", { exact: true }).fill(last);
    await page.getByLabel("Email", { exact: true }).fill(email);
    await page.getByLabel("Mot de passe initial").fill(password);
    await page.locator("#createTeam").selectOption("AZO");
    await page
      .getByRole("button", { name: "Créer et activer le compte", exact: true })
      .click();
    await page
      .getByRole("status")
      .filter({ hasText: "créé et activé" })
      .waitFor();
  }
  async function assign(page, email, role, supervisorId = null) {
    await page
      .getByRole("link", { name: "Organisation et zones", exact: true })
      .click();
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
  await createAccount(admin, "Direction", "Test", directorEmail);
  await assign(admin, directorEmail, "DIRECTEUR_COMMERCIAL");
  await login(director, directorEmail, password);
  await createAccount(director, "Superviseur", "Test", supervisorEmail);
  await assign(director, supervisorEmail, "SUPERVISEUR");
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
  await seller.getByRole("link", { name: "Rapports", exact: true }).click();
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
  await supervisor.getByRole("link", { name: "Rapports", exact: true }).click();
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
  await seller
    .getByRole("link", { name: "Tableau de bord", exact: true })
    .click();
  await expect(seller.locator("app-objectives")).toContainText(
    "188 à atteindre",
  );
  await expect(seller.locator("app-objectives")).toContainText(
    "41 à atteindre",
  );
  await expect(seller.locator("app-objectives")).toContainText("8 à atteindre");
  await supervisor
    .getByRole("link", { name: "Synthèses et rapport général", exact: true })
    .click();
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
  await director
    .getByRole("link", { name: "Synthèses et rapport général", exact: true })
    .click();
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
  await seller.getByRole("link", { name: "Rapports", exact: true }).click();
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
  await admin.getByRole("link", { name: "Commerciaux", exact: true }).click();
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
  await seller
    .getByRole("link", { name: "Mot de passe oublié ?", exact: true })
    .click();
  await seller.getByLabel("Email du compte", { exact: true }).fill(sellerEmail);
  await seller
    .getByRole("button", { name: "Recevoir le lien", exact: true })
    .click();
  await seller
    .getByRole("alert")
    .filter({ hasText: "pas encore configurée" })
    .waitFor();
  await login(seller, sellerEmail, freshPassword);
  await seller
    .getByRole("button", { name: "Déconnexion", exact: true })
    .click();
  await seller.waitForURL("**/connexion");
  await seller.goto(root + "/rapports");
  await seller.waitForURL("**/connexion");
  for (const page of [supervisor, director])
    for (const width of [360, 390, 768, 1440]) {
      await page.setViewportSize({ width, height: 1000 });
      await noOverflow(page);
    }
  assert.deepEqual(errors, []);
  console.log(
    "PASS: director nomination, supervisor zone, commercial assignment, scoped routing, precise correction, monthly goals, supervisor summary, director read-only general report, signup, activation, commercial creation, login, written report, return, stats report, resubmission, confirmation, AZO profile breakdown, named AZO registrations with add/remove/edit and persisted profiles, PDF download, admin password reset, stale session invalidation, forgot-password form, logout, 360/390/768/1440px without overflow; no browser errors.",
  );
  if (process.env.SMOKE_CROSS_ORIGIN) assert.ok(crossOriginWrites > 10);
  await browser.close();
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
