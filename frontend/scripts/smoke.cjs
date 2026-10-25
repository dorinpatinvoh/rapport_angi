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
  const errors = [],
    tag = Date.now(),
    sellerEmail = `seller-${tag}@example.test`,
    pendingEmail = `pending-${tag}@example.test`,
    password = `qa-password-${tag}`;
  const admin = await browser.newPage({
      viewport: { width: 1440, height: 1000 },
    }),
    seller = await browser.newPage({ viewport: { width: 390, height: 844 } });
  for (const page of [admin, seller])
    page.on("pageerror", (e) => errors.push(e.message));
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
  await login(seller, sellerEmail, password);
  await seller.getByRole("link", { name: "Rapports", exact: true }).click();
  await seller.getByRole("button", { name: "Rédiger un rapport" }).click();
  await seller.getByLabel("Titre du rapport").fill("Prospection mobile test");
  await seller
    .getByLabel("Déroulement de la journée")
    .fill("Visites à Cotonou. Plusieurs prospects intéressés.");
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
  await admin.getByRole("link", { name: "Rapports", exact: true }).click();
  let card = admin
    .locator(".report-card")
    .filter({ hasText: "Prospection mobile test" });
  await card
    .getByRole("button", { name: "Lire le rapport", exact: true })
    .click();
  await admin
    .getByLabel("Commentaire ou informations à confirmer")
    .fill("Confirmez les inscriptions attribuées.");
  await admin
    .getByRole("button", { name: "Renvoyer pour confirmation", exact: true })
    .click();
  await admin.getByRole("status").filter({ hasText: "renvoyé" }).waitFor();
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
  await seller
    .getByLabel("Comment souhaitez-vous faire votre bilan ?")
    .selectOption("STATS");
  await seller.getByLabel("Prospects contactés", { exact: true }).fill("12");
  await seller.getByLabel("Inscriptions attribuées", { exact: true }).fill("3");
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
  await admin.getByRole("button", { name: "Actualiser", exact: true }).click();
  await admin
    .locator(".report-card")
    .filter({ hasText: "Prospection mobile test" })
    .getByRole("button", { name: "Lire le rapport", exact: true })
    .click();
  await admin
    .getByRole("button", { name: "Confirmer le rapport", exact: true })
    .click();
  await admin
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
  await seller
    .getByRole("button", { name: "Déconnexion", exact: true })
    .click();
  await seller.waitForURL("**/connexion");
  await seller.goto(root + "/rapports");
  await seller.waitForURL("**/connexion");
  assert.deepEqual(errors, []);
  console.log(
    "PASS: signup, activation, commercial creation, login, written report, return, stats report, resubmission, confirmation, PDF download, logout, 360/390/768/1440px without overflow; no browser errors.",
  );
  await browser.close();
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
