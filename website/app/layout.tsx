import type { Metadata } from "next";
import "@fontsource-variable/bricolage-grotesque/standard.css";
import "@fontsource-variable/instrument-sans";
import "@fontsource-variable/jetbrains-mono";
import "./globals.css";
import { themeScript } from "@/components/ThemeToggle";

export const metadata: Metadata = {
  title: { default: "Feature Flags SDK", template: "%s | Feature Flags SDK" },
  description:
    "Integration guides for the Feature Flags SDK: one Kotlin Multiplatform evaluation engine for JVM servers, Android, iOS and the web.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body className="min-h-dvh antialiased">{children}</body>
    </html>
  );
}
