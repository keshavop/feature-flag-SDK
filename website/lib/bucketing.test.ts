import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { BUCKETS, bucketOf, inRollout } from "./bucketing.ts";
import { murmur3 } from "./murmur3.ts";

type Vector = { keyOrSeed: string; salt: string; value: string; input: string; hash: number; bucket: number };
const suite = JSON.parse(readFileSync(new URL("../../conformance/bucketing.json", import.meta.url), "utf8")) as {
  buckets: number;
  vectors: Vector[];
};

test("bucket count matches the conformance suite", () => {
  assert.equal(BUCKETS, suite.buckets);
});

test("every conformance vector hashes and buckets like the Kotlin core", () => {
  assert.ok(suite.vectors.length > 200);
  for (const v of suite.vectors) {
    assert.equal(murmur3(v.input), v.hash, `hash of ${JSON.stringify(v.input)}`);
    assert.equal(bucketOf(v.keyOrSeed, v.value, v.salt), v.bucket, `bucket of ${JSON.stringify(v.input)}`);
  }
});

test("raising a rollout never removes a user", () => {
  const steps = [0, 1, 5, 30, 50, 100];
  for (let i = 0; i < 2000; i++) {
    const key = `user-${i}`;
    let was = false;
    for (const p of steps) {
      const now = inRollout("new-checkout", key, p);
      if (was) assert.ok(now, `${key} dropped at ${p}%`);
      was = now;
    }
    assert.ok(was, `${key} not in at 100%`);
  }
});
