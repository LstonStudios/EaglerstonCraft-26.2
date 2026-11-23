const fs = require("fs");
const path = require("path");
const os = require("os");
const { spawnSync } = require("child_process");

const ROOT = path.resolve(__dirname, "..");
const BACKUP_DIR = path.join(ROOT, "backups");
const RUNTIME_DIR = path.join(ROOT, "runtime");
const BACKEND = path.join(RUNTIME_DIR, "backend");

function ensureBackupDir() {
  fs.mkdirSync(BACKUP_DIR, { recursive: true });
}

function safeName(name) {
  const cleaned = String(name || "manual")
    .trim()
    .replace(/[^a-z0-9._-]+/gi, "-")
    .replace(/^[-_.]+|[-_.]+$/g, "")
    .slice(0, 80);
  return cleaned || "backup";
}

function excludedRuntimePath(relativePath) {
  const normalized = relativePath.replace(/\\/g, "/");
  return normalized.startsWith("backend/logs/") ||
    normalized.startsWith("backend/cache/") ||
    normalized.startsWith("backend/libraries/") ||
    normalized.startsWith("backend/versions/") ||
    normalized.startsWith("backend/.paper/") ||
    normalized.startsWith("proxy/logs/") ||
    normalized.startsWith("proxy/.cache/");
}

function copyRuntime(destination) {
  for (const runtimeName of ["backend", "proxy"]) {
    const source = path.join(RUNTIME_DIR, runtimeName);
    const target = path.join(destination, "runtime", runtimeName);
    fs.mkdirSync(target, { recursive: true });
    fs.cpSync(source, target, {
      recursive: true,
      force: true,
      filter: sourcePath => {
        const relative = path.relative(RUNTIME_DIR, sourcePath);
        return !excludedRuntimePath(relative);
      }
    });
  }
}

function createBackup(name) {
  ensureBackupDir();
  const createdAt = new Date();
  const stamp = createdAt.toISOString().replace(/[:.]/g, "-");
  const folderName = `${stamp}-${safeName(name)}`;
  let destination = path.join(BACKUP_DIR, folderName);
  let counter = 2;
  while (fs.existsSync(destination)) destination = path.join(BACKUP_DIR, `${folderName}-${counter++}`);

  fs.mkdirSync(destination, { recursive: true });
  copyRuntime(destination);
  fs.writeFileSync(path.join(destination, "metadata.json"), JSON.stringify({
    name: safeName(name),
    createdAt: createdAt.toISOString()
  }, null, 2) + "\n");
  return { ...readBackup(destination), path: destination };
}

function readBackup(destination) {
  const isArchive = fs.statSync(destination).isFile();
  if (isArchive) {
    const fileName = path.basename(destination);
    return {
      name: fileName.replace(/\.tar\.gz$/i, ""),
      folderName: fileName,
      createdAt: null,
      type: "archive"
    };
  }

  const metadataPath = path.join(destination, "metadata.json");
  let metadata = {};
  if (fs.existsSync(metadataPath)) {
    try { metadata = JSON.parse(fs.readFileSync(metadataPath, "utf8")); } catch (_) {}
  }
  return {
    name: metadata.name || path.basename(destination),
    folderName: path.basename(destination),
    createdAt: metadata.createdAt || null,
    type: "directory"
  };
}

function listBackups() {
  ensureBackupDir();
  return fs.readdirSync(BACKUP_DIR, { withFileTypes: true })
    .filter(entry => entry.isDirectory() || (entry.isFile() && /\.tar\.gz$/i.test(entry.name)))
    .map(entry => {
      const fullPath = path.join(BACKUP_DIR, entry.name);
      return { ...readBackup(fullPath), path: fullPath };
    })
    .sort((a, b) => {
      const aCreated = Date.parse(a.createdAt);
      const bCreated = Date.parse(b.createdAt);
      const aTime = Number.isFinite(aCreated) ? aCreated : fs.statSync(a.path).mtimeMs;
      const bTime = Number.isFinite(bCreated) ? bCreated : fs.statSync(b.path).mtimeMs;
      return bTime - aTime;
    });
}

function findBackup(query) {
  const normalized = String(query || "").trim();
  if (!normalized) return null;

  const exactPath = path.join(BACKUP_DIR, path.basename(normalized));
  if (fs.existsSync(exactPath) && (fs.statSync(exactPath).isDirectory() || /\.tar\.gz$/i.test(exactPath))) {
    return { ...readBackup(exactPath), path: exactPath };
  }

  const matches = listBackups().filter(backup =>
    backup.name === safeName(normalized) || backup.folderName === normalized
  );
  if (matches.length) return matches[0];

  const number = Number(normalized);
  if (Number.isInteger(number) && number > 0) {
    const backups = listBackups();
    return backups[number - 1] || null;
  }
  return null;
}

function replaceDirectory(source, destination) {
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  const oldDestination = `${destination}.restore-old`;
  fs.rmSync(oldDestination, { recursive: true, force: true });
  if (fs.existsSync(destination)) fs.renameSync(destination, oldDestination);
  try {
    fs.cpSync(source, destination, { recursive: true, force: true });
  } catch (error) {
    fs.rmSync(destination, { recursive: true, force: true });
    if (fs.existsSync(oldDestination)) fs.renameSync(oldDestination, destination);
    throw error;
  }
  fs.rmSync(oldDestination, { recursive: true, force: true });
}

function extractArchive(archivePath, destination) {
  const listing = spawnSync("tar", ["-tzf", archivePath], {
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024
  });
  if (listing.error || listing.status !== 0) {
    throw new Error(`Could not read tar.gz archive${listing.stderr ? `: ${listing.stderr.trim()}` : "."}`);
  }

  const entries = listing.stdout.split(/\r?\n/).filter(Boolean);
  if (!entries.length) throw new Error("The tar.gz archive is empty.");
  for (const entry of entries) {
    const normalized = entry.replace(/\\/g, "/");
    if (normalized.startsWith("/") || normalized.split("/").includes("..")) {
      throw new Error("The archive contains an unsafe file path.");
    }
  }

  const verbose = spawnSync("tar", ["-tvzf", archivePath], {
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024
  });
  if (verbose.error || verbose.status !== 0) throw new Error("Could not inspect tar.gz archive contents.");
  if (verbose.stdout.split(/\r?\n/).filter(Boolean).some(line => !["-", "d"].includes(line[0]))) {
    throw new Error("The archive contains links or special files, which cannot be restored safely.");
  }

  const extracted = spawnSync("tar", ["--no-same-owner", "--no-same-permissions", "-xzf", archivePath, "-C", destination], {
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024
  });
  if (extracted.error || extracted.status !== 0) {
    throw new Error(`Could not extract tar.gz archive${extracted.stderr ? `: ${extracted.stderr.trim()}` : "."}`);
  }
}

function archiveDirectories(root) {
  const directories = [root];
  for (let index = 0; index < directories.length; index++) {
    for (const entry of fs.readdirSync(directories[index], { withFileTypes: true })) {
      if (entry.isDirectory()) directories.push(path.join(directories[index], entry.name));
    }
  }
  return directories;
}

function isBackendSnapshot(directory) {
  return fs.existsSync(path.join(directory, "server.properties")) &&
    ["world", "paper.jar", "plugins"].some(name => fs.existsSync(path.join(directory, name)));
}

function overlayDirectory(source, destination) {
  fs.mkdirSync(destination, { recursive: true });
  for (const entry of fs.readdirSync(source)) {
    replaceDirectory(path.join(source, entry), path.join(destination, entry));
  }
}

function restoreArchive(backup) {
  const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), "server-backup-"));
  try {
    extractArchive(backup.path, temporaryDirectory);
    const directories = archiveDirectories(temporaryDirectory);

    const runtimeRoot = directories.find(directory =>
      path.basename(directory) === "runtime" &&
      ["backend", "proxy"].some(name => fs.existsSync(path.join(directory, name)))
    );
    if (runtimeRoot) {
      for (const name of ["backend", "proxy"]) {
        const source = path.join(runtimeRoot, name);
        if (fs.existsSync(source)) replaceDirectory(source, path.join(RUNTIME_DIR, name));
      }
      return;
    }

    const backendDirectory = directories.find(directory =>
      path.basename(directory) === "backend" && isBackendSnapshot(directory)
    );
    if (backendDirectory) {
      replaceDirectory(backendDirectory, path.join(RUNTIME_DIR, "backend"));
      return;
    }

    const serverDirectory = directories.find(isBackendSnapshot);
    if (serverDirectory) {
      overlayDirectory(serverDirectory, BACKEND);
      return;
    }

    const worldDirectory = directories.find(directory => fs.existsSync(path.join(directory, "level.dat")));
    if (worldDirectory) {
      const propertiesPath = path.join(BACKEND, "server.properties");
      const properties = fs.existsSync(propertiesPath) ? fs.readFileSync(propertiesPath, "utf8") : "";
      const worldName = properties.match(/^\s*level-name\s*=\s*(.*?)\s*$/m)?.[1] || "world";
      const worldPath = path.resolve(BACKEND, worldName);
      if (worldPath === BACKEND || !worldPath.startsWith(`${BACKEND}${path.sep}`)) {
        throw new Error("The configured world name is not a safe folder path.");
      }
      replaceDirectory(worldDirectory, worldPath);
      return;
    }

    throw new Error("Archive layout not recognized. Include runtime/backend, a backend server folder, or a world folder containing level.dat.");
  } finally {
    fs.rmSync(temporaryDirectory, { recursive: true, force: true });
  }
}

function restoreBackup(backup) {
  if (!fs.existsSync(backup.path)) throw new Error("Backup no longer exists.");
  if (backup.type === "archive" || /\.tar\.gz$/i.test(backup.path)) {
    restoreArchive(backup);
    return backup;
  }

  const runtimeSource = path.join(backup.path, "runtime");
  if (!fs.existsSync(runtimeSource)) throw new Error("Backup is missing its runtime data.");
  replaceDirectory(path.join(runtimeSource, "backend"), path.join(RUNTIME_DIR, "backend"));
  replaceDirectory(path.join(runtimeSource, "proxy"), path.join(RUNTIME_DIR, "proxy"));
  return backup;
}

module.exports = {
  BACKUP_DIR,
  createBackup,
  listBackups,
  findBackup,
  restoreBackup
};
