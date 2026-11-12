import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { CodeBlock } from "@/components/CodeBlock";
import { SiteFooter, SiteHeader } from "@/components/SiteHeader";
import { Tabs } from "@/components/Tabs";
import { PLATFORMS, platformBySlug, type Step } from "@/lib/platforms";

export const dynamicParams = false;

export function generateStaticParams() {
  return PLATFORMS.map((p) => ({ platform: p.slug }));
}

export async function generateMetadata({ params }: { params: Promise<{ platform: string }> }): Promise<Metadata> {
  const platform = platformBySlug((await params).platform);
  return { title: platform ? `Integrate on ${platform.name}` : "Integrate" };
}

export default async function IntegratePage({ params }: { params: Promise<{ platform: string }> }) {
  const platform = platformBySlug((await params).platform);
  if (!platform) notFound();
  const index = PLATFORMS.indexOf(platform);
  const next = PLATFORMS[index + 1];

  return (
    <>
      <SiteHeader active="integrate" />
      <div className="mx-auto grid max-w-7xl gap-10 px-4 pt-10 sm:px-6 lg:grid-cols-[13rem_minmax(0,1fr)] lg:pt-14">
        <nav aria-label="Platforms" className="lg:sticky lg:top-24 lg:self-start">
          <ul className="flex gap-1 overflow-x-auto pb-1 lg:flex-col lg:overflow-visible">
            {PLATFORMS.map((p) => (
              <li key={p.slug}>
                <Link
                  href={`/integrate/${p.slug}/`}
                  aria-current={p.slug === platform.slug ? "page" : undefined}
                  className={
                    "block whitespace-nowrap rounded-md px-3 py-1.5 text-sm " +
                    (p.slug === platform.slug ? "bg-on-soft font-medium text-ink" : "text-muted hover:text-ink")
                  }
                >
                  {p.name}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <main className="min-w-0">
          <h1 className="display text-4xl font-bold sm:text-5xl">Integrate on {platform.name}</h1>
          <p className="mt-4 max-w-2xl text-lg text-muted">{platform.intro}</p>

          <dl className="mt-8 grid max-w-3xl gap-x-8 gap-y-4 border-y border-rule py-5 text-sm sm:grid-cols-3">
            <Fact term="Runs on" value={platform.runtime} />
            <Fact term="Key" value={platform.keyType} />
            <Fact term="Updates" value={platform.updates} />
          </dl>

          <ol className="mt-12 max-w-3xl">
            {platform.steps.map((step, i) => (
              <StepItem key={step.title} step={step} n={i + 1} last={i === platform.steps.length - 1} />
            ))}
          </ol>

          <div className="mt-14 flex max-w-3xl flex-wrap gap-4 border-t border-rule pt-6 text-sm">
            <Link href="/rollouts/" className="group mr-auto">
              <span className="block text-muted">Next, plan a release</span>
              <span className="font-medium group-hover:text-on">See how percentage rollouts behave</span>
            </Link>
            {next && (
              <Link href={`/integrate/${next.slug}/`} className="group text-right">
                <span className="block text-muted">Another platform</span>
                <span className="font-medium group-hover:text-on">{next.name}</span>
              </Link>
            )}
          </div>
        </main>
      </div>
      <SiteFooter />
    </>
  );
}

function Fact({ term, value }: { term: string; value: string }) {
  return (
    <div>
      <dt className="text-muted">{term}</dt>
      <dd className="mt-0.5 font-medium">{value}</dd>
    </div>
  );
}

function StepItem({ step, n, last }: { step: Step; n: number; last: boolean }) {
  const blocks = step.snippets.map((s) => <CodeBlock key={s.label} code={s.code} lang={s.lang} label={s.label} />);
  return (
    <li className="relative grid grid-cols-[2rem_minmax(0,1fr)] gap-x-4 pb-12 sm:grid-cols-[2.5rem_minmax(0,1fr)]">
      {!last && <span className="absolute top-10 bottom-2 left-4 w-px bg-rule sm:left-5" aria-hidden="true" />}
      <span className="display grid size-8 place-items-center rounded-full border-[1.5px] border-ink text-sm font-semibold sm:size-10 sm:text-base">
        {n}
      </span>
      <div className="min-w-0 pt-1 sm:pt-2">
        <h2 className="text-xl font-semibold">{step.title}</h2>
        {step.body && <p className="mt-1.5 text-muted">{step.body}</p>}
        <div className="mt-4">{blocks.length > 1 ? <Tabs labels={step.snippets.map((s) => s.label)}>{blocks}</Tabs> : blocks}</div>
      </div>
    </li>
  );
}
