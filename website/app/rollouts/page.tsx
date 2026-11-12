import type { Metadata } from "next";
import Link from "next/link";
import { CodeBlock } from "@/components/CodeBlock";
import { SiteFooter, SiteHeader } from "@/components/SiteHeader";
import { Playground } from "./Playground";

export const metadata: Metadata = { title: "Percentage rollouts" };

export default function RolloutsPage() {
  return (
    <>
      <SiteHeader active="rollouts" />
      <main className="mx-auto max-w-7xl px-4 pt-10 sm:px-6 lg:pt-14">
        <h1 className="display max-w-3xl text-4xl font-bold sm:text-5xl">Percentage rollouts</h1>
        <p className="mt-4 max-w-2xl text-lg text-muted">
          Give a feature to a share of your users, then raise the share as confidence grows. Pick a user and drag the
          percentage to see where they fall. The numbers below come from the same hash the SDKs run.
        </p>

        <Playground />

        <section className="mt-20 grid gap-12 lg:grid-cols-2">
          <div className="max-w-xl space-y-8">
            <div>
              <h2 className="display text-2xl font-bold">Why nobody flips back</h2>
              <p className="mt-2 text-muted">
                A user&apos;s bucket depends only on the flag key, the flag&apos;s salt and the user key. The rolled-out
                variation always covers buckets from 0 upward, so raising 1% to 30% extends that range and keeps everyone
                already inside it. Lowering the percentage removes the most recently added users first.
              </p>
            </div>
            <div>
              <h2 className="display text-2xl font-bold">Choosing what to bucket by</h2>
              <p className="mt-2 text-muted">
                Rollouts bucket by user key unless the rule says otherwise. Bucket by an organization key to give whole
                accounts the feature together, or by a device key for anonymous visitors. A context without the
                attribute always lands in the control group.
              </p>
            </div>
            <div>
              <h2 className="display text-2xl font-bold">Scheduling a ramp</h2>
              <p className="mt-2 text-muted">
                Schedules run in the flag service, not in the SDK. Each step is an ordinary flag update that reaches
                servers in about a second, so a ramp never depends on device clocks and you can pause it with one change.
              </p>
            </div>
            <p className="text-sm">
              <Link className="text-on underline underline-offset-4" href="/docs/adr-006-percentage-rollout-model/">
                Read the decision record for percentage rollouts
              </Link>
            </p>
          </div>
          <div className="min-w-0 space-y-6">
            <div>
              <h3 className="font-semibold">Test a rollout without a flag service</h3>
              <p className="mt-1 text-sm text-muted">The server SDK&apos;s test data source evaluates percentages exactly as production does.</p>
            </div>
            <CodeBlock
              label="RolloutTest.kt"
              lang="kotlin"
              code={`val td = TestDataSource()\nval client = FeatureFlagServer.create("test", ServerConfig(dataSource = ServerDataSource.Custom(td)))\n\ntd.update(td.flag("new-checkout").fallthroughPercentage(1.0))\nval early = users.filter { client.boolVariation("new-checkout", it, false) }\n\ntd.update(td.flag("new-checkout").fallthroughPercentage(30.0))\nassertTrue(early.all { client.boolVariation("new-checkout", it, false) })`}
            />
            <CodeBlock
              label="How a bucket is computed"
              lang="text"
              code={`input  = "<flag key>.<salt>.<user key>"\nhash   = murmur3_x86_32(utf8(input), seed = 0)\nbucket = hash mod 100000\non     = bucket < round(percent × 1000)`}
            />
          </div>
        </section>
      </main>
      <SiteFooter />
    </>
  );
}
