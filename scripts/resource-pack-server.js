const fs = require("fs");
const http = require("http");
const { RESOURCE_PACK_FILE, RESOURCE_PACK_ROUTE, getResourcePackSha1, portInUse } = require("./common");

function createResourcePackServer(port) {
  const numericPort = Number(port);
  if (!Number.isInteger(numericPort) || numericPort < 1 || numericPort > 65535) {
    throw new Error("Resource pack port must be an integer from 1 to 65535.");
  }
  if (!fs.statSync(RESOURCE_PACK_FILE).isFile()) {
    throw new Error(`Resource pack is not a file: ${RESOURCE_PACK_FILE}`);
  }

  const server = http.createServer((request, response) => {
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
    response.setHeader("Cross-Origin-Resource-Policy", "cross-origin");
    response.setHeader("X-Content-Type-Options", "nosniff");

    if (request.method === "OPTIONS") {
      response.writeHead(204);
      response.end();
      return;
    }

    const pathname = new URL(request.url, "http://localhost").pathname;
    if (pathname === "/healthz") {
      response.writeHead(200, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" });
      response.end(JSON.stringify({ ok: true, sha1: getResourcePackSha1() }));
      return;
    }
    if (pathname !== RESOURCE_PACK_ROUTE) {
      response.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      response.end("Not found");
      return;
    }
    if (request.method !== "GET" && request.method !== "HEAD") {
      response.setHeader("Allow", "GET, HEAD, OPTIONS");
      response.writeHead(405, { "Content-Type": "text/plain; charset=utf-8" });
      response.end("Method not allowed");
      return;
    }

    const stat = fs.statSync(RESOURCE_PACK_FILE);
    response.writeHead(200, {
      "Content-Type": "application/zip",
      "Content-Length": stat.size,
      "Cache-Control": "public, max-age=3600"
    });
    if (request.method === "HEAD") {
      response.end();
      return;
    }

    const stream = fs.createReadStream(RESOURCE_PACK_FILE);
    stream.on("error", error => {
      console.error("Resource pack stream failed:", error);
      response.destroy();
    });
    stream.pipe(response);
  });

  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(numericPort, "0.0.0.0", () => resolve(server));
  });
}

async function ensureResourcePackServer(port) {
  if (!(await portInUse(port))) return createResourcePackServer(port);

  let response;
  try {
    response = await fetch(`http://127.0.0.1:${Number(port)}/healthz`, { signal: AbortSignal.timeout(1000) });
  } catch (error) {
    throw new Error(`Port ${port} is already in use by a different service.`, { cause: error });
  }
  if (!response.ok) throw new Error(`Port ${port} is already in use by a different service.`);

  const health = await response.json();
  if (health.ok !== true || health.sha1 !== getResourcePackSha1()) {
    throw new Error(`Port ${port} is serving a different resource pack.`);
  }
  console.log(`Resource pack server already running on port ${port}`);
  return null;
}

if (require.main === module) {
  const { readConfig, getResourcePackUrl } = require("./common");
  const cfg = readConfig();
  createResourcePackServer(cfg.resourcePackPort).then(server => {
    console.log(`Serving resource pack at ${getResourcePackUrl(cfg)}`);
    const close = () => server.close(() => process.exit(0));
    process.on("SIGINT", close);
    process.on("SIGTERM", close);
  }).catch(error => {
    console.error(error.stack || error);
    process.exitCode = 1;
  });
}

module.exports = { createResourcePackServer, ensureResourcePackServer };
