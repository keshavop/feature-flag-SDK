import Link from "next/link";
import { CodeBlock } from "@/components/CodeBlock";
import { RolloutField } from "@/components/RolloutField";
import { SiteFooter, SiteHeader } from "@/components/SiteHeader";
import { listDocs } from "@/lib/docs";
import { PLATFORMS } from "@/lib/platforms";

export default function Home() {
  const design = listDocs().filter((d) => d.group !== "Guides");
  return (
    <>
      <SiteHeader />
      <main>
        <section className="mx-auto grid max-w-7xl gap-12 px-4 pt-14 pb-20 sm:px-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)] lg:gap-16 lg:pt-24">
          <div className="max-w-xl">
            <h1 className="display text-[2.75rem] font-bold sm:text-6xl">
              Roll out to 1%, then 30%. Everyone who had it keeps it.
            </h1>
            <p className="mt-6 text-lg text-muted">
              One Kotlin Multiplatform evaluation engine for JVM servers, Android, iOS and the browser. Flags evaluate
              locally from a snapshot, so every platform gives the same answer without a network call.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link
                href="/integrate/server/"
                className="rounded-full bg-on px-5 py-2.5 font-medium text-on-ink hover:opacity-90"
              >
                Start integrating
              </Link>
              <Link href="/rollouts/" className="rounded-full border border-rule px-5 py-2.5 font-medium hover:border-ink">
                Try the rollout playground
              </Link>
            </div>
          </div>
          <div className="lg:pt-2">
            <RolloutField compact initial={30} />
            <p className="mt-6 max-w-prose text-sm text-muted">
              Each square is a user. Its place in the rollout comes from a hash of the flag key and user key, the same
              hash every SDK runs, so raising the percentage only ever adds squares.
            </p>
          </div>
        </section>

        <section className="border-y border-rule bg-surface">
          <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6">
            <h2 className="display text-3xl font-bold sm:text-4xl">Pick your platform</h2>
            <p className="mt-3 max-w-2xl text-muted">
              Every SDK runs the same evaluation code. What differs is which key it uses, how much of your flag
              configuration it holds and how it stays current.
            </p>
            <div className="mt-10 text-sm">
              <div className="hidden grid-cols-[1.3fr_1fr_1fr_1fr] gap-6 border-b-[1.5px] border-ink py-2 font-semibold md:grid">
                <span>Platform</span>
                <span>Key</span>
                <span>What the SDK holds</span>
                <span>How it stays current</span>
              </div>
              <ul className="border-t-[1.5px] border-ink md:border-t-0">
                {PLATFORMS.map((p) => (
                  <li key={p.slug} className="border-b border-rule">
                    <Link
                      href={`/integrate/${p.slug}/`}
                      className="group grid gap-x-6 gap-y-2 py-4 md:grid-cols-[1.3fr_1fr_1fr_1fr]"
                    >
                      <span>
                        <span className="display block text-xl font-semibold group-hover:text-on">{p.name}</span>
                        <span className="block text-muted">{p.runtime}</span>
                      </span>
                      <Cell label="Key" value={p.keyType} />
                      <Cell label="What the SDK holds" value={p.data} />
                      <Cell label="How it stays current" value={p.updates} />
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-4 py-20 sm:px-6">
          <h2 className="display text-3xl font-bold sm:text-4xl">How a flag reaches your code</h2>
          <FlowDiagram />
          <div className="mt-12 grid gap-8 lg:grid-cols-2">
            <div className="max-w-xl">
              <h3 className="text-lg font-semibold">Reads never wait on the network</h3>
              <p className="mt-2 text-muted">
                A variation call reads an immutable snapshot that a background task swaps in when the flag service
                publishes a change. If the service is down, the SDK keeps serving the last snapshot, then your
                default. Calls never throw.
              </p>
            </div>
            <CodeBlock
              label="The same call, four platforms"
              lang="kotlin"
              code={`client.boolVariation("new-checkout", user, false)   // JVM server\nflags.boolVariation("new-checkout", false)          // Android\nflags.boolVariation(key: "new-checkout", default: false)  // iOS\nflags.boolVariation("new-checkout", false)          // Web`}
            />
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-4 sm:px-6">
          <h2 className="display text-3xl font-bold sm:text-4xl">Read the design</h2>
          <p className="mt-3 max-w-2xl text-muted">
            The design docs and decision records behind the SDK, rendered from the repository.
          </p>
          <ul className="mt-8 grid gap-x-10 sm:grid-cols-2 lg:grid-cols-3">
            {design.map((d) => (
              <li key={d.slug} className="border-t border-rule">
                <Link href={`/docs/${d.slug}/`} className="group block py-4">
                  <span className="font-medium group-hover:text-on">{d.title}</span>
                  <span className="mt-1 block text-sm text-muted">
                    {d.group === "Decisions" ? `Decision record, ${d.summary.toLowerCase()}` : d.summary}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      </main>
      <SiteFooter />
    </>
  );
}

/** Flag service → snapshot → local evaluation, with the server and client paths as two lanes. */
function FlowDiagram() {
  const lanes = [
    {
      who: "Server SDK",
      gets: "All flag rules",
      how: "Streamed over SSE, with polling as a fallback",
      eval: "Evaluates any user in-process",
    },
    {
      who: "Mobile and web SDKs",
      gets: "Values for one user",
      how: "Polled while the app is in the foreground",
      eval: "Reads values from memory and disk cache",
    },
  ];
  return (
    <div className="mt-10 grid gap-4 lg:grid-cols-[12rem_minmax(0,1fr)]">
      <div className="flex items-center rounded-lg border-[1.5px] border-ink p-4 lg:row-span-2">
        <div>
          <div className="font-semibold">Flag service</div>
          <div className="text-sm text-muted">Where you change flags and percentages</div>
        </div>
      </div>
      {lanes.map((lane) => (
        <div key={lane.who} className="grid items-stretch gap-2 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)_auto_minmax(0,1fr)]">
          <div className="rounded-lg border border-rule bg-surface p-4">
            <div className="text-sm text-muted">{lane.how}</div>
            <div className="mt-1 font-semibold">{lane.gets}</div>
          </div>
          <Arrow className="hidden sm:block" />
          <div className="rounded-lg border border-rule bg-surface p-4">
            <div className="text-sm text-muted">{lane.who}</div>
            <div className="mt-1 font-semibold">Snapshot in memory</div>
          </div>
          <Arrow className="hidden sm:block" />
          <div className="rounded-lg bg-on-soft p-4">
            <div className="text-sm text-muted">Your code</div>
            <div className="mt-1 font-semibold">{lane.eval}</div>
          </div>
        </div>
      ))}
    </div>
  );
}

function Cell({ label, value }: { label: string; value: string }) {
  return (
    <span className="grid grid-cols-[9rem_minmax(0,1fr)] gap-2 text-muted md:block">
      <span className="text-ink md:hidden">{label}</span>
      <span>{value}</span>
    </span>
  );
}

function Arrow({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 12" className={`w-6 self-center text-muted ${className}`} aria-hidden="true">
      <path d="M0 6h21M16 1l5 5-5 5" fill="none" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  );
}
