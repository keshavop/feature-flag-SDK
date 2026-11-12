import { murmur3 } from "./murmur3.ts";

/** Number of rollout buckets: 0.001 % granularity (ADR-006). */
export const BUCKETS = 100_000;

/** `murmur3("<flagKey>.<salt>.<value>") mod 100 000`, as in ff-core's Bucketer. */
export function bucketOf(flagKey: string, value: string, salt = ""): number {
  return murmur3(`${flagKey}.${salt}.${value}`) % BUCKETS;
}

/** A percentage maps to `round(p × 1000)` buckets, starting at bucket 0. */
export function weightOf(percent: number): number {
  return Math.round(Math.min(100, Math.max(0, percent)) * 1000);
}

/** The treatment range always starts at bucket 0, so raising the percentage only adds users. */
export function inRollout(flagKey: string, value: string, percent: number, salt = ""): boolean {
  return bucketOf(flagKey, value, salt) < weightOf(percent);
}
