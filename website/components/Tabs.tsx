"use client";

import { useId, useState, type ReactNode } from "react";

/** Tabs over pre-rendered panels, e.g. one code block per build tool or language. */
export function Tabs({ labels, children }: { labels: string[]; children: ReactNode[] }) {
  const [active, setActive] = useState(0);
  const id = useId();
  return (
    <div>
      <div role="tablist" aria-label="Variants" className="mb-2 flex flex-wrap gap-1">
        {labels.map((label, i) => (
          <button
            key={label}
            role="tab"
            id={`${id}-tab-${i}`}
            aria-selected={i === active}
            aria-controls={`${id}-panel-${i}`}
            tabIndex={i === active ? 0 : -1}
            onClick={() => setActive(i)}
            onKeyDown={(e) => {
              if (e.key === "ArrowRight" || e.key === "ArrowLeft") {
                const next = (active + (e.key === "ArrowRight" ? 1 : labels.length - 1)) % labels.length;
                setActive(next);
                document.getElementById(`${id}-tab-${next}`)?.focus();
              }
            }}
            className={
              "rounded-full px-3 py-1 text-sm transition-colors " +
              (i === active ? "bg-ink text-paper" : "text-muted hover:bg-surface hover:text-ink")
            }
          >
            {label}
          </button>
        ))}
      </div>
      {children.map((panel, i) => (
        <div key={labels[i]} role="tabpanel" id={`${id}-panel-${i}`} aria-labelledby={`${id}-tab-${i}`} hidden={i !== active}>
          {panel}
        </div>
      ))}
    </div>
  );
}
