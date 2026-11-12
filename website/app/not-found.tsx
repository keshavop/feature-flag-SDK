import Link from "next/link";
import { SiteHeader } from "@/components/SiteHeader";

export default function NotFound() {
  return (
    <>
      <SiteHeader />
      <main className="mx-auto max-w-7xl px-4 py-24 sm:px-6">
        <h1 className="display text-4xl font-bold">This page doesn&apos;t exist</h1>
        <p className="mt-4 text-muted">
          It may have moved. Go to the <Link className="text-on underline underline-offset-4" href="/docs/">docs index</Link> or the{" "}
          <Link className="text-on underline underline-offset-4" href="/">home page</Link>.
        </p>
      </main>
    </>
  );
}
