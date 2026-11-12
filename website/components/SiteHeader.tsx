import Link from "next/link";
import { REPO_URL } from "@/lib/site";
import { ThemeToggle } from "./ThemeToggle";

const NAV = [
  { href: "/integrate/server/", label: "Integrate" },
  { href: "/rollouts/", label: "Rollouts" },
  { href: "/docs/", label: "Docs" },
];

export function SiteHeader({ active }: { active?: "integrate" | "rollouts" | "docs" }) {
  return (
    <header className="sticky top-0 z-20 border-b border-rule bg-paper/90 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-7xl items-center gap-2 px-4 sm:px-6">
        <Link href="/" className="mr-auto flex items-center gap-2 font-semibold" aria-label="Feature Flags home">
          <Logo />
          <span className="hidden sm:inline">Feature Flags</span>
        </Link>
        <nav className="flex items-center gap-1 text-sm" aria-label="Main">
          {NAV.map((item) => {
            const isActive = active && item.href.startsWith(`/${active}`);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={isActive ? "page" : undefined}
                className={
                  "rounded-full px-3 py-1.5 " + (isActive ? "bg-ink text-paper" : "text-muted hover:bg-surface hover:text-ink")
                }
              >
                {item.label}
              </Link>
            );
          })}
          <a
            href={`${process.env.NEXT_PUBLIC_BASE_PATH ?? ""}/api/`}
            className="hidden rounded-full px-3 py-1.5 text-muted hover:bg-surface hover:text-ink md:inline"
          >
            API
          </a>
          <a href={`${REPO_URL}`} className="hidden rounded-full px-3 py-1.5 text-muted hover:bg-surface hover:text-ink md:inline">
            GitHub
          </a>
        </nav>
        <ThemeToggle />
      </div>
    </header>
  );
}

/** A toggle switch in the "on" position. */
export function Logo() {
  return (
    <svg viewBox="0 0 32 20" className="h-5 w-8" aria-hidden="true">
      <rect x="0.5" y="0.5" width="31" height="19" rx="9.5" fill="var(--on)" />
      <circle cx="22" cy="10" r="6.5" fill="var(--on-ink)" />
    </svg>
  );
}

export function SiteFooter() {
  return (
    <footer className="mt-24 border-t border-rule">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-8 text-sm text-muted sm:px-6">
        <span className="mr-auto">MIT License</span>
        <a className="hover:text-ink" href={REPO_URL}>
          Source on GitHub
        </a>
        <a className="hover:text-ink" href={`${REPO_URL}/releases`}>
          Releases
        </a>
      </div>
    </footer>
  );
}
