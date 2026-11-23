const fs = require("fs");
const path = require("path");
const os = require("os");
const readline = require("readline/promises");
const { stdin, stdout } = require("process");
const { spawn, spawnSync } = require("child_process");
const {
  ROOT, PROXY, BACKEND, readConfig, saveConfig, ensureDirs, checkJava25,
  patchClient, writeServerProperties, patchVelocityToml, ensureVelocityToml, patchEaglerConfigs,
  patchPaperVelocity, patchViaVersionConfig, walkFiles, portInUse, getResourcePackUrl
} = require("./common");
const { createClientServer } = require("./client-server");
const { ensureResourcePackServer } = require("./resource-pack-server");
const { createBackup, listBackups, findBackup, restoreBackup } = require("./backups");

function prefixLines(stream, prefix) {
  let pending = "";
  stream.setEncoding("utf8");
  stream.on("data", chunk => {
    pending += chunk;
    const lines = pending.split(/\r?\n/);
    pending = lines.pop();
    for (const line of lines) if (line.length) console.log(`${prefix}${line}`);
  });
  stream.on("end", () => { if (pending.length) console.log(`${prefix}${pending}`); });
}

function lanIps() {
  const out = [];
  for (const list of Object.values(os.networkInterfaces())) for (const a of list || []) {
    if (a.family === "IPv4" && !a.internal) out.push(a.address);
  }
  return out;
}

async function acceptEula(cfg) {
  if (cfg.acceptMinecraftEula) {
    fs.writeFileSync(path.join(BACKEND, "eula.txt"), "eula=true\n");
    return cfg;
  }
  console.log("\nMinecraft EULA acceptance is required before Paper can start:");
  console.log("https://aka.ms/MinecraftEULA\n");
  if (!stdin.isTTY) throw new Error("Set acceptMinecraftEula to true in config.json after reviewing the Minecraft EULA.");
  const rl = readline.createInterface({ input: stdin, output: stdout });
  const answer = (await rl.question("Do you accept the Minecraft EULA? Type YES to continue: ")).trim();
  rl.close();
  if (answer !== "YES") throw new Error("EULA not accepted. Server startup cancelled.");
  cfg.acceptMinecraftEula = true;
  saveConfig(cfg);
  fs.writeFileSync(path.join(BACKEND, "eula.txt"), "eula=true\n");
  return cfg;
}

function binariesPresent() {
  return fs.existsSync(path.join(BACKEND, "paper.jar")) &&
    fs.existsSync(path.join(PROXY, "velocity.jar")) &&
    fs.existsSync(path.join(PROXY, "plugins", "EaglerXServer.jar"));
}

function runSetupSync() {
  console.log("Required server binaries are missing. Running setup...");
  const r = spawnSync(process.execPath, [path.join(ROOT, "scripts", "install.js")], { cwd: ROOT, stdio: "inherit" });
  if (r.status !== 0) throw new Error("Automatic setup failed. Run `npm run setup` and retry.");
}

function findListenerCfg() {
  // Recent EaglerXServer releases use TOML; older releases used CFG.
  return walkFiles(path.join(PROXY, "plugins"), "listeners.toml")[0] ||
    walkFiles(path.join(PROXY, "plugins"), "listeners.cfg")[0] || null;
}

async function bootstrapVelocityIfNeeded(cfg, javaExecutable) {
  const velocityToml = path.join(PROXY, "velocity.toml");
  const viaConfig = path.join(PROXY, "plugins", "viaversion", "config.yml");
  if (fs.existsSync(velocityToml) && findListenerCfg() && fs.existsSync(viaConfig)) {
    patchVelocityToml(cfg);
    patchEaglerConfigs(cfg);
    patchViaVersionConfig(cfg);
    patchPaperVelocity();
    return;
  }

  // Seed/patch Velocity before the bootstrap process starts. This prevents Velocity
  // from ever touching its default port 25565 during first-run config generation.
  ensureVelocityToml(cfg);
  patchVelocityToml(cfg);

  console.log(`\n[first run] generating EaglerXServer configuration on port ${cfg.eaglerPort}...`);
  const child = spawn(javaExecutable, ["-Xms256M", "-Xmx512M", "-jar", "velocity.jar"], {
    cwd: PROXY,
    stdio: ["pipe", "pipe", "pipe"]
  });
  prefixLines(child.stdout, "[velocity-setup] ");
  prefixLines(child.stderr, "[velocity-setup] ");

  const deadline = Date.now() + 45000;
  while (Date.now() < deadline) {
    if (fs.existsSync(velocityToml) && findListenerCfg() && fs.existsSync(viaConfig)) break;
    if (child.exitCode !== null) break;
    await new Promise(r => setTimeout(r, 500));
  }

  if (child.exitCode === null) {
    try { child.stdin.write("shutdown\n"); } catch (_) {}
    await Promise.race([
      new Promise(r => child.once("exit", r)),
      new Promise(r => setTimeout(r, 6000))
    ]);
    if (child.exitCode === null) child.kill("SIGTERM");
  }

  if (!fs.existsSync(velocityToml)) throw new Error("Velocity did not generate velocity.toml during first-run setup.");
  if (!findListenerCfg()) throw new Error("EaglerXServer did not generate its listener config. Check the Velocity setup log above.");
  if (!fs.existsSync(viaConfig)) throw new Error("ViaVersion did not generate plugins/viaversion/config.yml. Check the Velocity setup log above.");
  patchVelocityToml(cfg);
  patchEaglerConfigs(cfg);
  patchViaVersionConfig(cfg);
  patchPaperVelocity();
  console.log("[first run] configuration generated and patched.");
}

async function waitForPort(port, timeoutMs = 120000) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    if (await portInUse(port)) return true;
    await new Promise(r => setTimeout(r, 500));
  }
  return false;
}

async function bootstrapPaperIfNeeded(cfg, javaExecutable) {
  const paperGlobal = path.join(BACKEND, "config", "paper-global.yml");
  if (fs.existsSync(paperGlobal)) return;

  console.log("\n[first run] generating Paper configuration...");
  const paper = spawn(javaExecutable, [`-Xms${cfg.paperMemoryMin}`, `-Xmx${cfg.paperMemoryMax}`, "-jar", "paper.jar", "--nogui"], {
    cwd: BACKEND, stdio: ["pipe", "pipe", "pipe"]
  });
  prefixLines(paper.stdout, "[paper-setup] ");
  prefixLines(paper.stderr, "[paper-setup] ");
  let startupDone = false;
  paper.stdout.on("data", chunk => {
    if (/\bDone\s+\([^\n]*\)!/.test(String(chunk))) startupDone = true;
  });

  // First boot may need extra time on low-cost ARM instances while Paper
  // generates world data and initializes its registries.
  const deadline = Date.now() + 300000;
  while (Date.now() < deadline && (!fs.existsSync(paperGlobal) || !startupDone) && paper.exitCode === null) {
    await new Promise(r => setTimeout(r, 500));
  }

  if (paper.exitCode !== null && (!fs.existsSync(paperGlobal) || !startupDone)) {
    throw new Error(`Paper exited during first-run configuration generation (code ${paper.exitCode}).`);
  }
  if (!fs.existsSync(paperGlobal) || !startupDone) {
    paper.kill("SIGTERM");
    throw new Error("Paper did not finish first-run configuration generation within 300 seconds.");
  }

  try { paper.stdin.write("stop\n"); } catch (_) {}
  await Promise.race([
    new Promise(resolve => paper.once("exit", resolve)),
    new Promise(resolve => setTimeout(resolve, 6000))
  ]);
  if (paper.exitCode === null) paper.kill("SIGTERM");
}

(async () => {
  ensureDirs();
  const javaExecutable = checkJava25();
  if (!binariesPresent()) runSetupSync();
  let cfg = readConfig();
  cfg = await acceptEula(cfg);
  patchClient(cfg);
  writeServerProperties(cfg);
  patchPaperVelocity();

  for (const [label, port] of [["client", cfg.clientPort], ["Eagler/Velocity", cfg.eaglerPort], ["Paper backend", cfg.backendPort]]) {
    if (await portInUse(port)) throw new Error(`${label} port ${port} is already in use. Stop the other program or change config.json.`);
  }

  await bootstrapPaperIfNeeded(cfg, javaExecutable);
  await bootstrapVelocityIfNeeded(cfg, javaExecutable);
  patchVelocityToml(cfg);
  patchEaglerConfigs(cfg);
  patchViaVersionConfig(cfg);
  if (!patchPaperVelocity()) throw new Error("Paper config/paper-global.yml is missing. Start Paper once or re-extract this full ZIP.");

  const resourcePackServer = await ensureResourcePackServer(cfg.resourcePackPort);
  let clientServer;
  try {
    clientServer = await createClientServer(cfg.clientPort);
  } catch (error) {
    if (resourcePackServer) resourcePackServer.close();
    throw error;
  }
  const children = [];

  console.log("\nstarting Paper 26.2...");
  let paper = spawn(javaExecutable, [`-Xms${cfg.paperMemoryMin}`, `-Xmx${cfg.paperMemoryMax}`, "-jar", "paper.jar", "--nogui"], {
    cwd: BACKEND, stdio: ["pipe", "pipe", "pipe"]
  });
  children.push({ child: paper, stop: "stop\n", name: "paper" });
  prefixLines(paper.stdout, "[paper] ");
  prefixLines(paper.stderr, "[paper] ");

  const paperReady = await waitForPort(cfg.backendPort, 120000);
  if (!paperReady) {
    try { paper.stdin.write("stop\n"); } catch (_) {}
    throw new Error(`Paper did not open port ${cfg.backendPort} within 120 seconds.`);
  }

  console.log("starting Velocity + EaglerXServer + ViaVersion + ViaBackwards...");
  let velocity = spawn(javaExecutable, [`-Xms${cfg.velocityMemoryMin}`, `-Xmx${cfg.velocityMemoryMax}`, "-jar", "velocity.jar"], {
    cwd: PROXY, stdio: ["pipe", "pipe", "pipe"]
  });
  children.push({ child: velocity, stop: "shutdown\n", name: "velocity" });
  prefixLines(velocity.stdout, "[velocity] ");
  prefixLines(velocity.stderr, "[velocity] ");

  const velocityReady = await waitForPort(cfg.eaglerPort, 60000);
  if (!velocityReady) throw new Error(`Velocity/EaglerXServer did not open port ${cfg.eaglerPort}. Check the logs above.`);

  console.log("\nREADY");
  console.log(`Client: http://localhost:${cfg.clientPort}`);
  console.log(`Eagler: ws://localhost:${cfg.eaglerPort}/`);
  console.log(`Resource pack: ${getResourcePackUrl(cfg)}`);
  console.log(`Paper:  127.0.0.1:${cfg.backendPort} (internal)`);
  for (const ip of lanIps()) {
    console.log(`LAN client: http://${ip}:${cfg.clientPort}`);
    console.log(`LAN Eagler: ws://${ip}:${cfg.eaglerPort}/`);
    console.log(`LAN resource pack: http://${ip}:${cfg.resourcePackPort}/swords-and-tnt.zip`);
  }
  console.log("\nThe server is already added to the Multiplayer screen.");
  console.log("Press Ctrl+C to stop everything cleanly.\n");

  let stopping = false;
  async function stopAll() {
    if (stopping) return;
    stopping = true;
    console.log("\nstopping...");
    clientServer.close();
    if (resourcePackServer) resourcePackServer.close();
    for (const p of children.slice().reverse()) {
      if (p.child.exitCode === null) {
        try { p.child.stdin.write(p.stop); } catch (_) {}
      }
    }
    await new Promise(r => setTimeout(r, 4000));
    for (const p of children) if (p.child.exitCode === null) p.child.kill("SIGTERM");
    setTimeout(() => process.exit(0), 500).unref();
  }
  process.on("SIGINT", stopAll);
  process.on("SIGTERM", stopAll);

  paper.on("exit", code => {
    if (!stopping) {
      console.error(`[paper] exited with code ${code}`);
      stopAll();
    }
  });
  velocity.on("exit", code => {
    if (!stopping) {
      console.error(`[velocity] exited with code ${code}`);
      stopAll();
    }
  });

  function printBackupList(backups) {
    if (!backups.length) {
      console.log("No backups found.");
      return;
    }
    console.log("Backups (newest to oldest):");
    backups.forEach((backup, index) => {
      const date = backup.createdAt ? new Date(backup.createdAt).toLocaleString() : "Unknown date";
      console.log(`${index + 1}. ${backup.name} - ${date}`);
    });
  }

  async function stopChildrenForRestore() {
    stopping = true;
    for (const processEntry of children.slice().reverse()) {
      if (processEntry.child.exitCode === null) {
        try { processEntry.child.stdin.write(processEntry.stop); } catch (_) {}
      }
    }
    await new Promise(resolve => setTimeout(resolve, 4000));
    for (const processEntry of children) {
      if (processEntry.child.exitCode === null) processEntry.child.kill("SIGTERM");
    }
    await new Promise(resolve => setTimeout(resolve, 500));
  }

  async function restartChildren() {
    children.length = 0;

    paper = spawn(javaExecutable, [`-Xms${cfg.paperMemoryMin}`, `-Xmx${cfg.paperMemoryMax}`, "-jar", "paper.jar", "--nogui"], {
      cwd: BACKEND, stdio: ["pipe", "pipe", "pipe"]
    });
    children.push({ child: paper, stop: "stop\n", name: "paper" });
    prefixLines(paper.stdout, "[paper] ");
    prefixLines(paper.stderr, "[paper] ");

    if (!(await waitForPort(cfg.backendPort, 120000))) {
      throw new Error(`Paper did not open port ${cfg.backendPort} after restoring backup.`);
    }

    velocity = spawn(javaExecutable, [`-Xms${cfg.velocityMemoryMin}`, `-Xmx${cfg.velocityMemoryMax}`, "-jar", "velocity.jar"], {
      cwd: PROXY, stdio: ["pipe", "pipe", "pipe"]
    });
    children.push({ child: velocity, stop: "shutdown\n", name: "velocity" });
    prefixLines(velocity.stdout, "[velocity] ");
    prefixLines(velocity.stderr, "[velocity] ");

    if (!(await waitForPort(cfg.eaglerPort, 60000))) {
      throw new Error("Velocity did not open its port after restoring backup.");
    }

    stopping = false;
    paper.on("exit", code => {
      if (!stopping) {
        console.error(`[paper] exited with code ${code}`);
        stopAll();
      }
    });
    velocity.on("exit", code => {
      if (!stopping) {
        console.error(`[velocity] exited with code ${code}`);
        stopAll();
      }
    });
    console.log("Backup restored and server restarted.");
  }

  async function restoreSelectedBackup(selection) {
    const backup = findBackup(selection);
    if (!backup) {
      console.log("Invalid backup selection.");
      return;
    }
    try {
      console.log(`Restoring backup: ${backup.name}`);
      await stopChildrenForRestore();
      restoreBackup(backup);
      await restartChildren();
    } catch (error) {
      console.error(`Restore failed: ${error.message}`);
      stopping = false;
    }
  }

  let pendingBackupSelection = false;
  const terminal = readline.createInterface({ input: stdin, output: stdout });
  terminal.on("line", line => {
    const [command, ...args] = line.trim().split(/\s+/);
    const argument = args.join(" ").trim();

    if (pendingBackupSelection) {
      pendingBackupSelection = false;
      void restoreSelectedBackup(argument || line.trim());
      return;
    }

    if (command === "backup") {
      if (!argument) {
        console.log("Usage: backup [name]");
        return;
      }
      try {
        if (paper.exitCode === null) paper.stdin.write("save-all flush\n");
        setTimeout(() => {
          try {
            const backup = createBackup(argument);
            console.log(`Backup created: ${backup.name}`);
            console.log(`Location: backups/${backup.folderName}`);
          } catch (error) {
            console.error(`Backup failed: ${error.message}`);
          }
        }, 1000);
      } catch (error) {
        console.error(`Backup failed: ${error.message}`);
      }
    } else if (command === "loadbackup") {
      if (argument) {
        void restoreSelectedBackup(argument);
      } else {
        printBackupList(listBackups());
        if (listBackups().length) pendingBackupSelection = true;
      }
    }
  });
})().catch(err => {
  console.error("\nSTARTUP FAILED\n");
  console.error(err && err.message ? err.message : err);
  console.error("\nRun `npm run doctor` for checks.");
  process.exit(1);
});
