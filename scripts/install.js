const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const {
  ROOT, PROXY, BACKEND, ensureDirs, readConfig, patchClient,
  ensureForwardingSecret, patchVelocityToml, patchPaperVelocity,
  patchViaVersionConfig, patchEaglerConfigs
} = require("./common");

const USER_AGENT = "eaglercraft-26.2-easy-server/1.2 (local server setup)";
const LOCAL_PAPER_PLUGINS = [
  "CustomSwords.jar",
  "CustomTNT.jar",
  "UsernameEffects.jar",
  "SpawnMobs.jar",
  "GameplayRewards.jar",
  "ShopSys.jar"
];
const PAPER_PLUGINS = [
  { key: "economySystem", name: "EconomySystem", projectId: "zCbXPyE8", fileName: "EconomySystem.jar" },
  { key: "serverHomes", name: "Server Homes", projectId: "ozgBn9Wz", fileName: "ServerHomes.jar" },
  { key: "easyTpa", name: "EasyTPA", projectId: "xc9IulMB", fileName: "EasyTPA.jar" },
  { key: "worldEdit", name: "WorldEdit", projectId: "1u6JkXh5", fileName: "WorldEdit.jar" },
  { key: "worldGuard", name: "WorldGuard", projectId: "DKY9btbd", fileName: "WorldGuard.jar" }
];

async function getJson(url) {
  const r = await fetch(url, { headers: { "User-Agent": USER_AGENT, "Accept": "application/json" }, redirect: "follow" });
  if (!r.ok) throw new Error(`HTTP ${r.status} ${r.statusText}: ${url}`);
  return r.json();
}

async function download(url, dest, expected = null) {
  const tmp = `${dest}.download`;
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  console.log(`downloading ${path.basename(dest)}...`);
  const r = await fetch(url, { headers: { "User-Agent": USER_AGENT }, redirect: "follow" });
  if (!r.ok) throw new Error(`Download failed (${r.status}): ${url}`);
  if (!r.body) throw new Error(`No response body: ${url}`);
  const file = fs.createWriteStream(tmp);
  const hash = expected ? crypto.createHash(expected.algorithm) : null;
  await new Promise((resolve, reject) => {
    const reader = r.body.getReader();
    function pump() {
      reader.read().then(({ done, value }) => {
        if (done) { file.end(resolve); return; }
        const chunk = Buffer.from(value);
        if (hash) hash.update(chunk);
        if (!file.write(chunk)) file.once("drain", pump);
        else pump();
      }).catch(reject);
    }
    file.on("error", reject);
    pump();
  });
  const stat = fs.statSync(tmp);
  if (expected && stat.size !== expected.size) {
    fs.rmSync(tmp, { force: true });
    throw new Error(`Downloaded file has unexpected size (${stat.size}, expected ${expected.size}): ${url}`);
  }
  if (hash && hash.digest("hex") !== expected.hash) {
    fs.rmSync(tmp, { force: true });
    throw new Error(`Downloaded file failed ${expected.algorithm.toUpperCase()} verification: ${url}`);
  }
  if (!expected && stat.size < 100000) {
    fs.rmSync(tmp, { force: true });
    throw new Error(`Downloaded file is unexpectedly small (${stat.size} bytes): ${url}`);
  }
  fs.renameSync(tmp, dest);
  console.log(`  saved ${path.relative(ROOT, dest)} (${(stat.size / 1024 / 1024).toFixed(1)} MB)`);
}

async function resolvePaper(version) {
  const url = `https://fill.papermc.io/v3/projects/paper/versions/${encodeURIComponent(version)}/builds`;
  const builds = await getJson(url);
  if (!Array.isArray(builds)) throw new Error("Unexpected Paper downloads response");
  const build = builds.find(b => b.channel === "STABLE") || builds[0];
  if (!build) throw new Error(`No Paper build found for Minecraft ${version}`);
  const dl = build.downloads && build.downloads["server:default"];
  if (!dl || !dl.url) throw new Error("Paper build has no server download URL");
  return { url: dl.url, version, build: build.id ?? build.number ?? "unknown", channel: build.channel || "unknown" };
}

async function resolveVelocity() {
  const project = await getJson("https://fill.papermc.io/v3/projects/velocity");
  const groups = project && project.versions ? Object.values(project.versions) : [];
  const versions = groups.flat().filter(Boolean);
  if (!versions.length) throw new Error("Could not determine current Velocity versions");
  for (const version of versions) {
    try {
      const builds = await getJson(`https://fill.papermc.io/v3/projects/velocity/versions/${encodeURIComponent(version)}/builds`);
      if (!Array.isArray(builds)) continue;
      const build = builds.find(b => b.channel === "STABLE");
      if (!build) continue;
      const dl = build.downloads && build.downloads["server:default"];
      if (dl && dl.url) return { url: dl.url, version, build: build.id ?? build.number ?? "unknown", channel: build.channel };
    } catch (_) {}
  }
  throw new Error("No stable Velocity build found");
}

async function resolveGitHubRelease(repo, preferredAssetPrefix) {
  const rel = await getJson(`https://api.github.com/repos/${repo}/releases/latest`);
  const assets = Array.isArray(rel.assets) ? rel.assets : [];
  const prefix = preferredAssetPrefix.toLowerCase();
  const asset = assets.find(a => a.name && a.name.toLowerCase() === `${prefix}.jar`) ||
    assets.find(a => a.name && a.name.toLowerCase().startsWith(prefix) && a.name.toLowerCase().endsWith(".jar") && !a.name.toLowerCase().includes("sources")) ||
    assets.find(a => a.name && a.name.toLowerCase().endsWith(".jar") && !a.name.toLowerCase().includes("sources"));
  if (!asset || !asset.browser_download_url) throw new Error(`Latest ${repo} release does not contain a usable JAR`);
  return { url: asset.browser_download_url, version: rel.tag_name || rel.name || "latest", asset: asset.name, published: rel.published_at || null };
}

async function resolveModrinthRelease(projectId, gameVersion, name) {
  const query = new URLSearchParams({
    game_versions: JSON.stringify([gameVersion]),
    loaders: JSON.stringify(["paper"])
  });
  const versions = await getJson(`https://api.modrinth.com/v2/project/${projectId}/version?${query}`);
  if (!Array.isArray(versions)) throw new Error(`Unexpected Modrinth versions response for ${name}`);

  const release = versions.find(v =>
    v.version_type === "release" &&
    Array.isArray(v.game_versions) && v.game_versions.includes(gameVersion) &&
    Array.isArray(v.loaders) && v.loaders.includes("paper")
  );
  if (!release) throw new Error(`No stable Paper release of ${name} supports Minecraft ${gameVersion}`);
  const file = Array.isArray(release.files) && (release.files.find(f => f.primary && /\.jar$/i.test(f.filename)) ||
    release.files.find(f => /\.jar$/i.test(f.filename)));
  if (!file || !file.url || !Number.isSafeInteger(file.size) || !/^[a-f0-9]{128}$/i.test(file.hashes && file.hashes.sha512 || "")) {
    throw new Error(`Modrinth release for ${name} is missing a verifiable JAR`);
  }
  return {
    url: file.url,
    version: release.version_number,
    release: release.name,
    projectId,
    modrinthVersionId: release.id,
    published: release.date_published,
    asset: file.filename,
    size: file.size,
    hashAlgorithm: "sha512",
    hash: file.hashes.sha512
  };
}

async function resolveHangarRelease(owner, slug, gameVersion, name) {
  const query = new URLSearchParams({ platform: "PAPER", limit: "100" });
  const versions = await getJson(`https://hangar.papermc.io/api/v1/projects/${encodeURIComponent(owner)}/${encodeURIComponent(slug)}/versions?${query}`);
  if (!versions || !Array.isArray(versions.result)) throw new Error(`Unexpected Hangar versions response for ${name}`);

  const release = versions.result.find(v =>
    v.visibility === "public" &&
    v.channel && v.channel.name === "Release" &&
    Array.isArray(v.platformDependencies && v.platformDependencies.PAPER) &&
    v.platformDependencies.PAPER.includes(gameVersion)
  );
  if (!release) throw new Error(`No stable Paper release of ${name} supports Minecraft ${gameVersion}`);
  const downloadInfo = release.downloads && release.downloads.PAPER;
  const file = downloadInfo && downloadInfo.fileInfo;
  if (!downloadInfo || !downloadInfo.downloadUrl || !file ||
      !Number.isSafeInteger(file.sizeBytes) || !/^[a-f0-9]{64}$/i.test(file.sha256Hash || "")) {
    throw new Error(`Hangar release for ${name} is missing a verifiable JAR`);
  }
  return {
    url: downloadInfo.downloadUrl,
    version: release.name,
    release: release.name,
    owner,
    projectSlug: slug,
    published: release.createdAt,
    asset: file.name,
    size: file.sizeBytes,
    hashAlgorithm: "sha256",
    hash: file.sha256Hash
  };
}

async function ensureArtifact(file, resolver, versions, key) {
  if (fs.existsSync(file)) {
    console.log(`${path.basename(file)} already present`);
    return;
  }
  const info = await resolver();
  const expected = info.size && info.hashAlgorithm && info.hash
    ? { size: info.size, algorithm: info.hashAlgorithm, hash: info.hash }
    : null;
  await download(info.url, file, expected);
  versions[key] = info;
}

function removeReplacedPlugins() {
  for (const fileName of ["DZEconomy.jar", "NameColor.jar", "CustomItems.jar", "EasyPrefixGUI.jar"]) {
    const file = path.join(BACKEND, "plugins", fileName);
    if (!fs.existsSync(file)) continue;
    fs.rmSync(file);
    console.log(`removed ${path.relative(ROOT, file)}`);
  }
}

async function installPaperPlugins(cfg, versions) {
  for (const plugin of PAPER_PLUGINS) {
    await ensureArtifact(
      path.join(BACKEND, "plugins", plugin.fileName),
      () => plugin.projectId
        ? resolveModrinthRelease(plugin.projectId, cfg.minecraftVersion, plugin.name)
        : resolveHangarRelease(plugin.owner, plugin.projectSlug, cfg.minecraftVersion, plugin.name),
      versions,
      plugin.key
    );
  }
  for (const fileName of LOCAL_PAPER_PLUGINS) {
    const file = path.join(BACKEND, "plugins", fileName);
    if (!fs.existsSync(file)) throw new Error(`Missing bundled plugin JAR: ${path.relative(ROOT, file)}`);
    console.log(`${fileName} already present`);
  }
  removeReplacedPlugins();
}

async function main() {
  ensureDirs();
  const cfg = readConfig();
  const versions = {};
  const pluginsOnly = process.argv.includes("--plugins-only");

  if (!pluginsOnly) {
    patchClient(cfg);
    ensureForwardingSecret();
    await ensureArtifact(path.join(BACKEND, "paper.jar"), () => resolvePaper(cfg.minecraftVersion), versions, "paper");
    await ensureArtifact(path.join(PROXY, "velocity.jar"), resolveVelocity, versions, "velocity");
    await ensureArtifact(path.join(PROXY, "plugins", "EaglerXServer.jar"), () => resolveGitHubRelease("lax1dude/eaglerxserver", "EaglerXServer"), versions, "eaglerXServer");
    await ensureArtifact(path.join(PROXY, "plugins", "ViaVersion.jar"), () => resolveGitHubRelease("ViaVersion/ViaVersion", "ViaVersion"), versions, "viaVersion");
    await ensureArtifact(path.join(PROXY, "plugins", "ViaBackwards.jar"), () => resolveGitHubRelease("ViaVersion/ViaBackwards", "ViaBackwards"), versions, "viaBackwards");
  }

  await installPaperPlugins(cfg, versions);

  if (!pluginsOnly) {
    patchVelocityToml(cfg);
    patchPaperVelocity();
    patchViaVersionConfig(cfg);
    patchEaglerConfigs(cfg);
  }

  const versionsPath = path.join(ROOT, "runtime", "versions.json");
  let existing = {};
  try { existing = JSON.parse(fs.readFileSync(versionsPath, "utf8")); } catch (_) {}
  delete existing.dzEconomy;
  delete existing.nameColor;
  delete existing.easyPrefix;
  delete existing.customItems;
  fs.writeFileSync(versionsPath, JSON.stringify({ ...existing, ...versions, installedAt: new Date().toISOString() }, null, 2) + "\n");

  if (pluginsOnly) {
    console.log("\nPaper plugins installed");
  } else {
    console.log("\nsetup complete");
    console.log("stack: EaglerXServer + ViaVersion + ViaBackwards + Velocity + Paper 26.2");
    console.log(`ViaVersion backend protocol pinned to ${Number(cfg.backendProtocol || 776)}`);
    console.log("next: npm start");
  }
}

main().catch(err => {
  console.error("\nsetup failed:");
  console.error(err && err.stack ? err.stack : err);
  console.error("\nRun `npm run setup` to retry after fixing your network connection.");
  process.exitCode = 1;
});
