"use client";

import { useEffect, useId, useState } from "react";

/** Renders a mermaid diagram from the docs in the active theme; falls back to the source if rendering fails. */
export function Mermaid({ chart }: { chart: string }) {
  const id = useId().replace(/[^a-zA-Z0-9]/g, "");
  const [svg, setSvg] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const render = async () => {
      try {
        const mermaid = (await import("mermaid")).default;
        const dark =
          document.documentElement.dataset.theme === "dark" ||
          (!document.documentElement.dataset.theme && matchMedia("(prefers-color-scheme: dark)").matches);
        mermaid.initialize({ startOnLoad: false, theme: dark ? "dark" : "neutral", fontFamily: "inherit", securityLevel: "strict" });
        const { svg } = await mermaid.render(`m${id}${dark ? "d" : "l"}`, chart);
        if (!cancelled) setSvg(svg);
      } catch {
        if (!cancelled) setFailed(true);
      }
    };
    render();
    const observer = new MutationObserver(render);
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
    return () => {
      cancelled = true;
      observer.disconnect();
    };
  }, [chart, id]);

  if (failed || !svg) {
    return (
      <pre className="overflow-x-auto rounded-lg border border-rule bg-code p-4 font-mono text-[0.8125rem]">
        <code>{chart}</code>
      </pre>
    );
  }
  return (
    <div
      className="overflow-x-auto rounded-lg border border-rule bg-surface p-4 [&_svg]:mx-auto [&_svg]:h-auto [&_svg]:max-w-full"
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  );
}
