#!/usr/bin/env node
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const requestedRoot = process.argv[2] ?? process.cwd();
const root = path.resolve(requestedRoot);
const results = [];

function report(level, message) {
  results.push({ level, message });
}

function pass(message) {
  report("PASS", message);
}

function warn(message) {
  report("WARN", message);
}

function fail(message) {
  report("FAIL", message);
}

function readText(file) {
  if (!fs.existsSync(file)) return "";
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function walkFiles(dir, predicate) {
  if (!fs.existsSync(dir)) return [];
  const files = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) files.push(...walkFiles(full, predicate));
    else if (predicate(full)) files.push(full);
  }
  return files;
}

function parseProperties(text) {
  const properties = new Map();
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#") || line.startsWith("!")) continue;
    const separator = line.search(/[:=]/);
    if (separator < 0) continue;
    properties.set(line.slice(0, separator).trim(), line.slice(separator + 1).trim());
  }
  return properties;
}

function firstProperty(properties, names) {
  for (const name of names) {
    const value = properties.get(name);
    if (value) return value;
  }
  return "";
}

function checkPinnedValue(label, value, expected, exactProfile) {
  if (!value) {
    const message = `${label} is not declared in gradle.properties`;
    if (exactProfile) fail(message);
    else warn(message);
    return;
  }
  if (value === expected) pass(`${label} is ${expected}`);
  else if (exactProfile) fail(`${label} must be ${expected}; found ${value}`);
  else fail(`${label} targets ${value}; this skill supports ${expected}`);
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function combinedBuildText() {
  return ["build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"]
    .map((name) => readText(path.join(root, name)))
    .join("\n");
}

if (!fs.existsSync(root) || !fs.statSync(root).isDirectory()) {
  console.error(`FAIL Project root does not exist: ${root}`);
  process.exit(2);
}

const properties = parseProperties(readText(path.join(root, "gradle.properties")));
const buildText = combinedBuildText();
const modId = firstProperty(properties, ["mod_id", "modId"]);
const exactSourceRoot = path.join(root, "src", "main", "java", "net", "bullettrain", "xenopixelsmod");
const exactProfile = modId === "xenopixelsmod" || fs.existsSync(exactSourceRoot);

console.log(`Xenopixels project audit: ${root}`);
console.log(`Profile: ${exactProfile ? "exact XenoPixels repository" : "external XenoPixels addon"}`);

const minecraftVersion = firstProperty(properties, ["minecraft_version", "minecraftVersion"]);
const neoForgeVersion = firstProperty(properties, ["neo_version", "neoforge_version", "neoForgeVersion"]);
const javaVersion = firstProperty(properties, ["java_version", "javaVersion"]);

checkPinnedValue("Minecraft version", minecraftVersion, "1.21.1", exactProfile);
checkPinnedValue("NeoForge version", neoForgeVersion, "21.1.248", exactProfile);
checkPinnedValue("Java version", javaVersion, "21", exactProfile);

if (exactProfile) {
  const apiRoot = path.join(exactSourceRoot, "api");
  if (fs.existsSync(apiRoot)) pass("published API package exists under net.bullettrain.xenopixelsmod.api");
  else fail("published API package is missing from net.bullettrain.xenopixelsmod.api");

  if (/dragonminez-2\.1\.3\.jar/i.test(buildText)) {
    pass("DragonMineZ dependency references dragonminez-2.1.3.jar");
  } else {
    fail("DragonMineZ dependency must reference dragonminez-2.1.3.jar");
  }

  const expectedHash = properties.get("dragonminez_sha256") ?? "";
  if (/^[a-f0-9]{64}$/i.test(expectedHash)) {
    pass("DragonMineZ SHA-256 property is present");
  } else {
    fail("DragonMineZ SHA-256 property is missing or invalid");
  }

  const dmzJar = path.join(root, "libs", "dragonminez-2.1.3.jar");
  if (fs.existsSync(dmzJar) && /^[a-f0-9]{64}$/i.test(expectedHash)) {
    const actualHash = sha256(dmzJar);
    if (actualHash.toLowerCase() === expectedHash.toLowerCase()) pass("DragonMineZ jar hash matches gradle.properties");
    else fail(`DragonMineZ jar hash mismatch: expected ${expectedHash}, found ${actualHash}`);
  } else if (!fs.existsSync(dmzJar)) {
    warn("DragonMineZ jar is absent, so its content hash was not checked");
  }

  const addonNetwork = walkFiles(exactSourceRoot, (file) => /AddonNetwork\.java$/.test(file));
  if (addonNetwork.length > 0) pass("separate AddonNetwork registry is present");
  else warn("AddonNetwork.java was not found; verify addon packets do not extend the main sequential channel");
} else {
  const javaFiles = walkFiles(path.join(root, "src"), (file) => file.endsWith(".java"));
  const internalImports = [];
  for (const file of javaFiles) {
    const text = readText(file);
    if (/import\s+net\.bullettrain\.xenopixelsmod\.(?!api(?:\.|;))/.test(text)) {
      internalImports.push(path.relative(root, file).replaceAll(path.sep, "/"));
    }
  }
  if (internalImports.length === 0) {
    pass("external addon uses only the published XenoPixels API package");
  } else {
    fail(`external addon imports internal XenoPixels packages: ${internalImports.join(", ")}`);
  }
}

const sourceText = walkFiles(path.join(root, "src"), (file) => file.endsWith(".java"))
  .map(readText)
  .join("\n");
const optionalIntegrations = [
  ["DragonMineZ", /com\.dragonminez\./],
  ["MyNPCs", /espi\.mynpcs\./],
  ["CustomNPCs", /noppes\.npcs\./],
];
for (const [name, pattern] of optionalIntegrations) {
  if (pattern.test(sourceText)) warn(`${name} classes are referenced; verify optional-mod gating and dedicated-server classloading`);
}
if (/create aeronautics|aeronautics|sable/i.test(`${buildText}\n${sourceText}`)) {
  warn("ship integration detected; verify exact Create Aeronautics/Sable symbols and ship-space transforms against installed dependencies");
}

for (const result of results) console.log(`${result.level} ${result.message}`);
const failures = results.filter((result) => result.level === "FAIL").length;
const warnings = results.filter((result) => result.level === "WARN").length;
console.log(`Summary: ${failures} failure(s), ${warnings} warning(s)`);
process.exitCode = failures > 0 ? 1 : 0;
