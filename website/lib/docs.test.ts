import assert from "node:assert/strict";
import { test } from "node:test";
import { headingsOf, listDocs, loadDoc, resolveDocLink } from "./docs.ts";

test("lists guides, design docs and all ADRs", () => {
  const docs = listDocs();
  assert.ok(docs.some((d) => d.slug === "hld"));
  assert.equal(docs.filter((d) => d.group === "Decisions").length >= 6, true);
  assert.equal(new Set(docs.map((d) => d.slug)).size, docs.length);
  assert.ok(loadDoc("adr-006-percentage-rollout-model"));
});

test("rewrites relative links", () => {
  assert.equal(resolveDocLink("./hld.md#65-percentage-rollouts", "getting-started.md"), "/docs/hld/#65-percentage-rollouts");
  assert.equal(resolveDocLink("./adr/ADR-004-deterministic-bucketing-murmur3.md", "hld.md"), "/docs/adr-004-deterministic-bucketing-murmur3/");
  assert.equal(resolveDocLink("../hld.md", "adr/ADR-001-kotlin-multiplatform-core.md"), "/docs/hld/");
  assert.equal(resolveDocLink("./adr/", "hld.md"), "/docs/#decisions");
  assert.equal(
    resolveDocLink("../.github/workflows/release.yml", "releasing.md"),
    "https://github.com/keshavop/feature-flag-SDK/blob/main/.github/workflows/release.yml",
  );
  assert.equal(resolveDocLink("#top", "hld.md"), "#top");
});

test("headings get the same ids as GitHub", () => {
  const hs = headingsOf("## 7. Delivery contract (what the SDK depends on)\n```\n## not a heading\n```\n### 6.5 Percentage rollouts\n");
  assert.deepEqual(
    hs.map((h) => h.id),
    ["7-delivery-contract-what-the-sdk-depends-on", "65-percentage-rollouts"],
  );
});
