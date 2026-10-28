// Starts isolated in-memory H2 + the built frontend. Never targets the user's PostgreSQL.
const { spawn } = require("node:child_process"),
  path = require("node:path"),
  fs = require("node:fs"),
  os = require("node:os");
const root = path.resolve(__dirname, "../.."),
  log = path.join(os.tmpdir(), "rapport-smoke-backend.log");
const args = [
  "mvnw",
  ...(process.env.SMOKE_MAVEN_OFFLINE ? ["-o"] : []),
  ...(process.env.SMOKE_MAVEN_SETTINGS
    ? ["-s", process.env.SMOKE_MAVEN_SETTINGS]
    : []),
  "spring-boot:run",
  "-Dspring-boot.run.useTestClasspath=true",
  "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:h2:mem:smoke;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.datasource.password= --spring.flyway.enabled=true --app.bootstrap.email=qa-admin@example.test --app.bootstrap.password=qa-test-password-2026 --server.port=8080 --server.address=127.0.0.1 --app.public-url=http://127.0.0.1:4200",
];
if (process.env.SMOKE_JAVA_VERSION)
  args.push("-Djava.version=" + process.env.SMOKE_JAVA_VERSION);
const backend = spawn("bash", args, {
    cwd: path.join(root, "backend"),
    detached: true,
    stdio: ["ignore", fs.openSync(log, "w"), fs.openSync(log, "a")],
  }),
  preview = spawn(
    process.execPath,
    [path.join(__dirname, "serve-preview.cjs")],
    { detached: true, stdio: "inherit" },
  );
function cleanup() {
  for (const p of [backend, preview])
    try {
      process.kill(-p.pid, "SIGTERM");
    } catch {}
}
(async () => {
  let ready = false;
  for (let i = 0; i < 600; i++) {
    if (backend.exitCode !== null)
      throw Error(
        "QA backend failed: " + fs.readFileSync(log, "utf8").slice(-6000),
      );
    const data = fs.readFileSync(log, "utf8");
    if (data.includes("Application run failed")) throw Error(data.slice(-6000));
    // Bootstrap runs just after Started BackendApplication: allow a brief wait before testing login.
    if (data.includes("Started BackendApplication")) {
      await new Promise((r) => setTimeout(r, 1500));
      ready = true;
      break;
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  if (!ready) throw Error("QA startup timeout; see " + log);
  const smoke = spawn(process.execPath, [path.join(__dirname, "smoke.cjs")], {
    stdio: "inherit",
    env: {
      ...process.env,
      SMOKE_ADMIN_EMAIL: "qa-admin@example.test",
      SMOKE_ADMIN_PASSWORD: "qa-test-password-2026",
    },
  });
  const code = await new Promise((r) => smoke.on("exit", r));
  cleanup();
  process.exit(typeof code === "number" ? code : 1);
})().catch((e) => {
  console.error(e);
  cleanup();
  process.exit(1);
});
process.on("SIGINT", () => {
  cleanup();
  process.exit(130);
});
