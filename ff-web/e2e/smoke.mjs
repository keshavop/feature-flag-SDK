// Browser smoke test for the npm package (ff-web/build/npm): a local server provides the package, a page that uses it
// and a fake flag service; headless Chromium checks evaluation, change listeners and identify end to end. Also prints
// the gzipped bundle size. Run after `./gradlew :ff-web:prepareNpmPackage`:  node ff-web/e2e/smoke.mjs
import { createServer } from "node:http";
import { readFileSync, readdirSync } from "node:fs";
import { createRequire } from "node:module";
import { gzipSync } from "node:zlib";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const { chromium } = require("playwright");
const pkg = join(dirname(fileURLToPath(import.meta.url)), "..", "build", "npm");

let evaluations = 0;
const page = `<!doctype html><meta charset="utf-8"><div id="out">loading</div>
<script type="module">
  import { initialize } from "/pkg/feature-flags.mjs";
  const out = document.getElementById("out");
  const client = await initialize("cli-key", { kind: "user", key: "alice" }, { baseUri: location.origin, sendEvents: false });
  const changes = [];
  client.on("change:greeting", (value) => changes.push(value));
  const first = client.stringVariation("greeting", "default");
  const result = await client.identify({ kind: "user", key: "bob" });
  out.textContent = JSON.stringify({ first, afterIdentify: client.stringVariation("greeting", "default"), result, changes,
    enabled: client.boolVariation("enabled", false) });
</script>`;

const server = createServer((req, res) => {
  if (req.url === "/") return res.end(page);
  if (req.url.startsWith("/pkg/")) {
    res.setHeader("Content-Type", "text/javascript");
    return res.end(readFileSync(join(pkg, req.url.slice(5))));
  }
  if (req.url.startsWith("/sdk/v1/client/evaluate")) {
    let body = "";
    req.on("data", (c) => (body += c));
    return req.on("end", () => {
      evaluations++;
      if (req.headers.authorization !== "cli-key") return res.writeHead(401).end();
      const who = JSON.parse(body).key;
      res.setHeader("Content-Type", "application/json");
      res.end(JSON.stringify({ schemaVersion: 1, flags: {
        greeting: { value: `hello ${who}`, variation: 1, version: 1 },
        enabled: { value: true, variation: 1, version: 1 } } }));
    });
  }
  res.writeHead(404).end();
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const origin = `http://127.0.0.1:${server.address().port}`;

const browser = await chromium.launch();
try {
  const tab = await browser.newPage();
  const errors = [];
  tab.on("pageerror", (e) => errors.push(e.message));
  tab.on("console", (m) => { if (m.type() === "error") console.log("console error:", m.text()); });
  tab.on("requestfailed", (r) => console.log("request failed:", r.url(), r.failure()?.errorText));
  tab.on("response", (r) => { if (r.status() >= 400) console.log("http", r.status(), r.url()); });
  await tab.goto(origin + "/");
  try {
    await tab.waitForFunction(() => document.getElementById("out").textContent.startsWith("{"), null, { timeout: 15000 });
  } catch (e) {
    console.log("errors:", errors, "evaluations:", evaluations, "text:", await tab.textContent("#out"));
    throw e;
  }
  const result = JSON.parse(await tab.textContent("#out"));
  // identify clears the previous user's values first (null), so one user's flags never show for another
  const expected = { first: "hello alice", afterIdentify: "hello bob", result: "FRESH", changes: [null, "hello bob"], enabled: true };
  const ok = JSON.stringify(result) === JSON.stringify(expected) && errors.length === 0 && evaluations === 2;
  console.log(ok ? "browser smoke test passed" : "browser smoke test FAILED", JSON.stringify({ result, errors, evaluations }));
  const files = readdirSync(pkg).filter((f) => f.endsWith(".mjs"));
  const gz = files.reduce((sum, f) => sum + gzipSync(readFileSync(join(pkg, f)), { level: 9 }).length, 0);
  console.log(`bundle: ${files.length} modules, ${(gz / 1024).toFixed(1)} KiB gzipped (no tree shaking)`);
  process.exitCode = ok ? 0 : 1;
} finally {
  await browser.close();
  server.close();
}
