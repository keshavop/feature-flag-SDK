import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Markdown } from "@/components/Markdown";
import { SiteFooter, SiteHeader } from "@/components/SiteHeader";
import { headingsOf, listDocs, loadDoc, REPO_URL } from "@/lib/docs";

export const dynamicParams = false;

export function generateStaticParams() {
  return listDocs().map((d) => ({ slug: d.slug }));
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const doc = loadDoc((await params).slug);
  return { title: doc?.meta.title ?? "Docs" };
}

export default async function DocPage({ params }: { params: Promise<{ slug: string }> }) {
  const doc = loadDoc((await params).slug);
  if (!doc) notFound();
  const docs = listDocs();
  const toc = headingsOf(doc.markdown).filter((h) => h.depth === 2);

  return (
    <>
      <SiteHeader active="docs" />
      <div className="mx-auto grid max-w-7xl gap-10 px-4 pt-10 sm:px-6 lg:grid-cols-[14rem_minmax(0,1fr)] lg:pt-14 xl:grid-cols-[14rem_minmax(0,1fr)_14rem]">
        <nav aria-label="Docs" className="hidden lg:sticky lg:top-24 lg:block lg:max-h-[calc(100dvh-7rem)] lg:self-start lg:overflow-y-auto">
          {(["Guides", "Design", "Decisions"] as const).map((group) => (
            <div key={group} className="mb-6">
              <div className="mb-1 px-3 text-sm font-semibold">{group}</div>
              <ul>
                {docs
                  .filter((d) => d.group === group)
                  .map((d) => (
                    <li key={d.slug}>
                      <Link
                        href={`/docs/${d.slug}/`}
                        aria-current={d.slug === doc.meta.slug ? "page" : undefined}
                        className={
                          "block rounded-md px-3 py-1 text-sm " +
                          (d.slug === doc.meta.slug ? "bg-on-soft font-medium text-ink" : "text-muted hover:text-ink")
                        }
                      >
                        {shortTitle(d.title)}
                      </Link>
                    </li>
                  ))}
              </ul>
            </div>
          ))}
        </nav>

        <main className="min-w-0">
          <Link href="/docs/" className="text-sm text-muted hover:text-ink lg:hidden">
            All docs
          </Link>
          <h1 className="display mt-2 max-w-3xl text-4xl font-bold sm:text-5xl lg:mt-0">{doc.meta.title}</h1>
          <div className="mt-8">
            <Markdown source={doc.markdown} file={doc.meta.file} />
          </div>
          <p className="mt-12 max-w-[72ch] border-t border-rule pt-6 text-sm text-muted">
            <a className="hover:text-ink" href={`${REPO_URL}/edit/main/docs/${doc.meta.file}`}>
              Edit this page on GitHub
            </a>
          </p>
        </main>

        {toc.length > 1 && (
          <aside className="hidden xl:sticky xl:top-24 xl:block xl:max-h-[calc(100dvh-7rem)] xl:self-start xl:overflow-y-auto">
            <div className="mb-2 text-sm font-semibold">On this page</div>
            <ul className="space-y-1 border-l border-rule text-sm">
              {toc.map((h) => (
                <li key={h.id}>
                  <a href={`#${h.id}`} className="-ml-px block border-l border-transparent pl-3 text-muted hover:border-ink hover:text-ink">
                    {h.text}
                  </a>
                </li>
              ))}
            </ul>
          </aside>
        )}
      </div>
      <SiteFooter />
    </>
  );
}

/** "ADR-004: Deterministic bucketing with MurmurHash3 (x86_32) over …" → "4. Deterministic bucketing with MurmurHash3". */
function shortTitle(title: string): string {
  return title
    .replace(/^ADR-0*(\d+):\s*/, "$1. ")
    .replace(/\s+(—|\().*$/, "")
    .replace(/\s+over .*$/, "");
}
