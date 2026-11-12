import { highlight } from "@/lib/highlight";
import { CopyButton } from "./CopyButton";

/** A highlighted, copyable code block. `label` names the file or language in the block's top bar. */
export async function CodeBlock({ code, lang, label }: { code: string; lang: string; label?: string }) {
  const html = await highlight(code.replace(/\n$/, ""), lang);
  return (
    <div className="overflow-hidden rounded-lg border border-rule bg-code">
      <div className="flex items-center justify-between border-b border-rule py-1 pr-1 pl-3">
        <span className="text-xs text-muted">{label ?? lang}</span>
        <CopyButton text={code} />
      </div>
      <div className="overflow-x-auto px-4 py-3" dangerouslySetInnerHTML={{ __html: html }} />
    </div>
  );
}
