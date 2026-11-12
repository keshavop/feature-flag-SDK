import type { Metadata } from "next";
import Link from "next/link";
import { SiteFooter, SiteHeader } from "@/components/SiteHeader";
import { listDocs, type DocGroup } from "@/lib/docs";

export const metadata: Metadata = { title: "Docs" };

const GROUPS: { group: DocGroup; id: string; intro: string }[] = [
  { group: "Guides", id: "guides", intro: "Setting up and shipping the SDK." },
  { group: "Design", id: "design", intro: "How the SDK is built and why it behaves the way it does." },
  { group: "Decisions", id: "decisions", intro: "Architecture decision records, each with the options that lost." },
];

export default function DocsIndex() {
  const docs = listDocs();
  return (
    <>
      <SiteHeader active="docs" />
      <main className="mx-auto max-w-7xl px-4 pt-10 sm:px-6 lg:pt-14">
        <h1 className="display text-4xl font-bold sm:text-5xl">Docs</h1>
        <p className="mt-4 max-w-2xl text-lg text-muted">
          Everything in the repository&apos;s <code className="font-mono text-[0.85em]">docs</code> folder. For step-by-step
          setup, start with the <Link className="text-on underline underline-offset-4" href="/integrate/server/">integration guides</Link>.
        </p>
        <div className="mt-12 space-y-14">
          {GROUPS.map(({ group, id, intro }) => (
            <section key={group} id={id} className="scroll-mt-24 grid gap-6 lg:grid-cols-[16rem_minmax(0,1fr)]">
              <div>
                <h2 className="display text-2xl font-bold">{group}</h2>
                <p className="mt-1 text-sm text-muted">{intro}</p>
              </div>
              <ul>
                {docs
                  .filter((d) => d.group === group)
                  .map((d) => (
                    <li key={d.slug} className="border-t border-rule last:border-b">
                      <Link href={`/docs/${d.slug}/`} className="group flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1 py-4">
                        <span className="font-medium group-hover:text-on">{d.title}</span>
                        <span className="text-sm text-muted">{d.summary}</span>
                      </Link>
                    </li>
                  ))}
              </ul>
            </section>
          ))}
        </div>
      </main>
      <SiteFooter />
    </>
  );
}
