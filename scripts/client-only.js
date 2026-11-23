const os = require("os");
const { readConfig, patchClient, getResourcePackUrl } = require("./common");
const { createClientServer } = require("./client-server");
const { ensureResourcePackServer } = require("./resource-pack-server");

function lanIps() {
  const out = [];
  for (const list of Object.values(os.networkInterfaces())) for (const a of list || []) {
    if (a.family === "IPv4" && !a.internal) out.push(a.address);
  }
  return out;
}

(async () => {
  const cfg = readConfig();
  patchClient(cfg);
  await ensureResourcePackServer(cfg.resourcePackPort);
  await createClientServer(cfg.clientPort);
  console.log(`Client: http://localhost:${cfg.clientPort}`);
  console.log(`Resource pack: ${getResourcePackUrl(cfg)}`);
  for (const ip of lanIps()) console.log(`LAN:    http://${ip}:${cfg.clientPort}`);
  console.log("Press Ctrl+C to stop.");
})().catch(err => { console.error(err.stack || err); process.exit(1); });
