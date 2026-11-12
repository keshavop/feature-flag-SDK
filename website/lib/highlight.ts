import { createHighlighter, type Highlighter } from "shiki";

const LANGS = ["kotlin", "java", "xml", "swift", "js", "tsx", "sh", "json", "yaml", "text"] as const;
let highlighter: Promise<Highlighter> | undefined;

/** Highlights at build time with both themes; globals.css picks one from the active theme. */
export async function highlight(code: string, lang: string): Promise<string> {
  highlighter ??= createHighlighter({ themes: ["github-light", "github-dark"], langs: [...LANGS] });
  const h = await highlighter;
  const known = (LANGS as readonly string[]).includes(lang) ? lang : "text";
  return h.codeToHtml(code, {
    lang: known,
    themes: { light: "github-light", dark: "github-dark" },
    defaultColor: false,
  });
}
