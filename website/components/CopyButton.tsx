"use client";

import { useState } from "react";

export function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      type="button"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(text);
          setCopied(true);
          setTimeout(() => setCopied(false), 1500);
        } catch {
          setCopied(false);
        }
      }}
      className="rounded-md px-2 py-1 text-xs font-medium text-muted hover:bg-surface hover:text-ink"
      aria-live="polite"
    >
      {copied ? "Copied" : "Copy"}
    </button>
  );
}
