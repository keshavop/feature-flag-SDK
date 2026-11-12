"use client";

import { useState } from "react";
import { formatPercent, RolloutField } from "@/components/RolloutField";
import { BUCKETS, bucketOf, weightOf } from "@/lib/bucketing";
import { murmur3 } from "@/lib/murmur3";

const RAMP = [1, 5, 30, 100];

export function Playground() {
  const [flagKey, setFlagKey] = useState("new-checkout");
  const [userKey, setUserKey] = useState("user-42");
  const [percent, setPercent] = useState(30);

  const flag = flagKey.trim() || "new-checkout";
  const input = `${flag}..${userKey}`;
  const hash = murmur3(input);
  const bucket = bucketOf(flag, userKey);
  const threshold = weightOf(percent);
  const on = bucket < threshold;
  const firstOn = RAMP.find((p) => bucket < weightOf(p));

  return (
    <div className="mt-10 grid gap-10 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <div className="min-w-0">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Flag key" value={flagKey} onChange={setFlagKey} />
          <Field label="User key" value={userKey} onChange={setUserKey} />
        </div>

        <div className="mt-8 rounded-xl border border-rule bg-surface p-5">
          <div className="flex items-baseline justify-between gap-4">
            <div className="min-w-0">
              <div className="truncate text-sm text-muted">{userKey || "(empty key)"}</div>
              <div className={"display text-4xl font-bold " + (on ? "text-on" : "")} aria-live="polite">
                {on ? "Gets the feature" : "Not yet"}
              </div>
            </div>
            <div className="display shrink-0 text-4xl tabular-nums">{formatPercent(percent)}</div>
          </div>

          <Ruler bucket={bucket} threshold={threshold} />

          <dl className="mt-6 grid grid-cols-[auto_minmax(0,1fr)] gap-x-6 gap-y-1.5 text-sm">
            <dt className="text-muted">Hash input</dt>
            <dd className="truncate font-mono text-[0.8125rem]">{input}</dd>
            <dt className="text-muted">MurmurHash3</dt>
            <dd className="font-mono text-[0.8125rem] tabular-nums">{hash.toLocaleString("en-US")}</dd>
            <dt className="text-muted">Bucket</dt>
            <dd className="font-mono text-[0.8125rem] tabular-nums">
              {bucket.toLocaleString("en-US")} of {BUCKETS.toLocaleString("en-US")}
            </dd>
            <dt className="text-muted">Included below</dt>
            <dd className="font-mono text-[0.8125rem] tabular-nums">{threshold.toLocaleString("en-US")}</dd>
          </dl>
        </div>

        <div className="mt-6">
          <h2 className="text-sm text-muted">This user across a typical ramp</h2>
          <ol className="mt-2 grid grid-cols-4 gap-1">
            {RAMP.map((p) => {
              const inAt = bucket < weightOf(p);
              return (
                <li key={p}>
                  <button
                    type="button"
                    onClick={() => setPercent(p)}
                    className={
                      "w-full rounded-md px-2 py-2 text-left text-sm transition-colors " +
                      (inAt ? "bg-on text-on-ink" : "bg-off text-ink")
                    }
                  >
                    <span className="block font-semibold tabular-nums">{p}%</span>
                    <span className="block text-xs opacity-80">{inAt ? "In" : "Out"}</span>
                  </button>
                </li>
              );
            })}
          </ol>
          <p className="mt-2 text-sm text-muted">
            {firstOn === undefined
              ? "Only a full rollout reaches this user."
              : `Joins at ${firstOn}% and stays in for every step after.`}
          </p>
        </div>
      </div>

      <div className="min-w-0">
        <RolloutField flagKey={flag} percent={percent} onPercentChange={setPercent} highlightKey={userKey} />
        <p className="mt-4 text-sm text-muted">
          A thousand users, <code className="font-mono text-[0.85em]">user-1</code> to{" "}
          <code className="font-mono text-[0.85em]">user-1000</code>, bucketed for{" "}
          <code className="font-mono text-[0.85em]">{flag}</code>. Change the flag key and they reshuffle; that is how
          two flags at 10% reach different users.
        </p>
      </div>
    </div>
  );
}

function Field({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label className="block">
      <span className="text-sm text-muted">{label}</span>
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        spellCheck={false}
        autoComplete="off"
        className="mt-1 block w-full rounded-lg border border-rule bg-surface px-3 py-2 font-mono text-sm outline-none focus:border-on"
      />
    </label>
  );
}

/** The 0–100,000 bucket line: the rolled-out range from 0, and where this user sits. */
function Ruler({ bucket, threshold }: { bucket: number; threshold: number }) {
  const at = (bucket / BUCKETS) * 100;
  return (
    <div className="mt-6" aria-hidden="true">
      <div className="relative h-3 rounded-full bg-off">
        <div className="absolute inset-y-0 left-0 rounded-full bg-on transition-[width] duration-300" style={{ width: `${(threshold / BUCKETS) * 100}%` }} />
        <div className="absolute -top-1.5 h-6 w-[3px] -translate-x-1/2 rounded-full bg-ink" style={{ left: `${at}%` }} />
      </div>
      <div className="mt-1.5 flex justify-between text-xs text-muted tabular-nums">
        <span>0</span>
        <span>50,000</span>
        <span>100,000</span>
      </div>
    </div>
  );
}
