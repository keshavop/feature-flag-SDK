import Link from "next/link";
import ReactMarkdown, { type Components } from "react-markdown";
import rehypeSlug from "rehype-slug";
import remarkGfm from "remark-gfm";
import type { Element, Text } from "hast";
import { resolveDocLink } from "@/lib/docs";
import { CodeBlock } from "./CodeBlock";
import { Mermaid } from "./Mermaid";

function textOf(node: Element | Text): string {
  return node.type === "text" ? node.value : node.children.map((c) => textOf(c as Element | Text)).join("");
}

/** Renders one of the repository's Markdown docs, with links rewritten to site routes. */
export function Markdown({ source, file }: { source: string; file: string }) {
  const components: Components = {
    a: ({ href = "", children }) => {
      const to = resolveDocLink(href, file);
      return to.startsWith("/") ? <Link href={to}>{children}</Link> : <a href={to}>{children}</a>;
    },
    pre: ({ node }) => {
      const code = node?.children.find((c): c is Element => c.type === "element" && c.tagName === "code");
      if (!code) return null;
      const className = (code.properties.className as string[] | undefined) ?? [];
      const lang = className.find((c) => c.startsWith("language-"))?.slice(9) ?? "text";
      const text = textOf(code);
      if (lang === "mermaid") return <Mermaid chart={text} />;
      return <CodeBlock code={text} lang={lang} />;
    },
    table: ({ children }) => (
      <div className="overflow-x-auto">
        <table>{children}</table>
      </div>
    ),
  };
  return (
    <div className="prose">
      <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeSlug]} components={components}>
        {source}
      </ReactMarkdown>
    </div>
  );
}
