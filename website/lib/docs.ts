import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import GithubSlugger from "github-slugger";

/** The repository's Markdown docs, read at build time so the site never drifts from them. */
const DOCS_DIR = path.join(process.cwd(), "..", "docs");
import { REPO_URL } from "./site.ts";
export { REPO_URL };

export type DocGroup = "Guides" | "Design" | "Decisions";
export type DocMeta = { slug: string; file: string; title: string; group: DocGroup; summary: string };

const GUIDES: Record<string, string> = {
  "getting-started.md": "Install and call each SDK in a few lines.",
  "releasing.md": "Cut one version across Maven Central, npm and Swift Package Manager.",
};
const DESIGN: Record<string, string> = {
  "hld.md": "Goals, architecture, sync model and the delivery contract.",
  "lld.md": "Modules, data model, evaluation algorithm and wire formats.",
  "threat-model.md": "What the SDKs trust, what they protect and how.",
  "implementation-plan.md": "Phases 0–6 and what each one shipped.",
};

function titleOf(markdown: string, fallback: string): string {
  const heading = markdown.match(/^#\s+(.+)$/m)?.[1] ?? fallback;
  return heading.replace(/\s+—\s+Universal Feature Flag SDK$/, "");
}

function slugOf(file: string): string {
  return path.basename(file, ".md").toLowerCase();
}

function read(file: string): string {
  return readFileSync(path.join(DOCS_DIR, file), "utf8");
}

export function listDocs(): DocMeta[] {
  const entries: DocMeta[] = [];
  for (const [file, summary] of Object.entries(GUIDES)) {
    entries.push({ slug: slugOf(file), file, title: titleOf(read(file), file), group: "Guides", summary });
  }
  for (const [file, summary] of Object.entries(DESIGN)) {
    entries.push({ slug: slugOf(file), file, title: titleOf(read(file), file), group: "Design", summary });
  }
  for (const name of readdirSync(path.join(DOCS_DIR, "adr")).filter((f) => f.endsWith(".md")).sort()) {
    const file = `adr/${name}`;
    const md = read(file);
    const status = md.match(/\*\*Status:\*\*\s*(\w+)/)?.[1] ?? "";
    entries.push({ slug: slugOf(name), file, title: titleOf(md, name), group: "Decisions", summary: status });
  }
  return entries;
}

export type Heading = { depth: number; text: string; id: string };

export function loadDoc(slug: string): { meta: DocMeta; markdown: string } | undefined {
  const meta = listDocs().find((d) => d.slug === slug);
  if (!meta) return undefined;
  // Drop the H1: the page renders the title itself.
  const markdown = read(meta.file).replace(/^#\s+.+\n/, "");
  return { meta, markdown };
}

/**
 * Maps a link inside a doc to a site route: other docs become /docs/<slug>/, anchors are kept, and anything else in the
 * repository points at GitHub.
 */
export function resolveDocLink(href: string, fromFile: string): string {
  if (/^(https?:|mailto:|#)/.test(href)) return href;
  const [target, anchor] = href.split("#");
  const resolved = path.posix.normalize(path.posix.join(path.posix.dirname(`docs/${fromFile}`), target));
  const hash = anchor ? `#${anchor}` : "";
  if (resolved === "docs/adr" || resolved === "docs/adr/") return `/docs/#decisions`;
  if (resolved.startsWith("docs/") && resolved.endsWith(".md")) return `/docs/${slugOf(resolved)}/${hash}`;
  return `${REPO_URL}/blob/main/${resolved}${hash}`;
}

/** Level-2 and level-3 headings for the on-page contents, with the ids rehype-slug gives them. */
export function headingsOf(markdown: string): Heading[] {
  const slugger = new GithubSlugger();
  const out: Heading[] = [];
  let inFence = false;
  for (const line of markdown.split("\n")) {
    if (/^\s*```/.test(line)) inFence = !inFence;
    if (inFence) continue;
    const m = line.match(/^(#{1,6})\s+(.+?)\s*#*\s*$/);
    if (!m) continue;
    const text = m[2]
      .replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
      .replace(/[`*_]/g, "")
      .trim();
    const id = slugger.slug(text);
    if (m[1].length === 2 || m[1].length === 3) out.push({ depth: m[1].length, text, id });
  }
  return out;
}
