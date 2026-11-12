"use client";

import { useMemo, useState } from "react";
import { bucketOf, weightOf } from "@/lib/bucketing";

const USERS = 1000;
const PRESETS = [1, 5, 30, 100];

/**
 * A thousand users, each one cell, bucketed exactly as the SDKs do it. Moving the slider lights cells from the lowest
 * buckets up, so a cell that is on stays on as the rollout grows.
 */
export function RolloutField({
  flagKey = "new-checkout",
  percent: controlled,
  onPercentChange,
  initial = 30,
  highlightKey,
  compact = false,
}: {
  flagKey?: string;
  percent?: number;
  onPercentChange?: (p: number) => void;
  initial?: number;
  highlightKey?: string;
  compact?: boolean;
}) {
  const [own, setOwn] = useState(initial);
  const percent = controlled ?? own;
  const setPercent = (p: number) => (onPercentChange ? onPercentChange(p) : setOwn(p));
  const [hover, setHover] = useState<number | null>(null);

  const buckets = useMemo(
    () => Array.from({ length: USERS }, (_, i) => bucketOf(flagKey, `user-${i + 1}`)),
    [flagKey],
  );
  const threshold = weightOf(percent);
  const lit = buckets.reduce((n, b) => n + (b < threshold ? 1 : 0), 0);
  const highlighted = highlightKey?.match(/^user-(\d+)$/)?.[1];
  const highlightIndex = highlighted && Number(highlighted) <= USERS ? Number(highlighted) - 1 : -1;

  return (
    <div>
      <div
        className="grid gap-[2px] sm:gap-[3px]"
        style={{ gridTemplateColumns: `repeat(${compact ? 40 : 50}, minmax(0, 1fr))` }}
        onMouseLeave={() => setHover(null)}
        role="img"
        aria-label={`${lit} of ${USERS} users get ${flagKey} at ${percent}%`}
      >
        {buckets.map((b, i) => {
          const on = b < threshold;
          return (
            <div
              key={i}
              onMouseEnter={() => setHover(i)}
              className={
                "aspect-square rounded-[2px] transition-colors duration-300 " +
                (on ? "bg-on" : "bg-off") +
                (i === highlightIndex ? " ring-2 ring-ink ring-offset-1 ring-offset-paper" : "")
              }
            />
          );
        })}
      </div>

      <p className="mt-3 min-h-[1.5rem] text-sm text-muted" aria-live="polite">
        {hover === null ? (
          <>
            <span className="font-semibold text-ink tabular-nums">{lit.toLocaleString("en-US")}</span> of 1,000 users get{" "}
            <code className="font-mono text-[0.85em]">{flagKey}</code>
          </>
        ) : (
          <>
            <span className="font-semibold text-ink">user-{hover + 1}</span> is in bucket{" "}
            <span className="tabular-nums">{buckets[hover].toLocaleString("en-US")}</span>, so it is{" "}
            {buckets[hover] < threshold ? "in" : "not in"} the rollout
          </>
        )}
      </p>

      <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-3">
        <label className="flex min-w-0 flex-1 basis-56 items-center gap-3">
          <span className="sr-only">Rollout percentage</span>
          <input
            type="range"
            min={0}
            max={100}
            step={0.5}
            value={percent}
            onChange={(e) => setPercent(Number(e.target.value))}
            className="h-2 w-full cursor-pointer accent-[var(--on)]"
          />
          <span className="display w-16 shrink-0 text-right text-2xl tabular-nums">{formatPercent(percent)}</span>
        </label>
        <div className="flex gap-1" role="group" aria-label="Preset percentages">
          {PRESETS.map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => setPercent(p)}
              aria-pressed={percent === p}
              className={
                "rounded-full border px-3 py-1 text-sm tabular-nums transition-colors " +
                (percent === p ? "border-on bg-on text-on-ink" : "border-rule text-muted hover:border-ink hover:text-ink")
              }
            >
              {p}%
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

export function formatPercent(p: number): string {
  return `${Number.isInteger(p) ? p : p.toFixed(1)}%`;
}
