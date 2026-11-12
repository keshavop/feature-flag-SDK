/**
 * Integration guides, one per platform. Snippets follow docs/getting-started.md and the public APIs of each module, so
 * update both together.
 */
export type Snippet = { label: string; lang: string; code: string };
export type Step = { title: string; body: string; snippets: Snippet[] };
export type Platform = {
  slug: string;
  name: string;
  runtime: string;
  keyType: string;
  data: string;
  updates: string;
  intro: string;
  steps: Step[];
};

const SERVER: Platform = {
  slug: "server",
  name: "Server",
  runtime: "Kotlin and Java on the JVM",
  keyType: "Server key (secret)",
  data: "Every rule, evaluated in-process",
  updates: "SSE stream, polling fallback",
  intro:
    "The server SDK holds the full rule set in memory and evaluates each call locally, without locks or network calls. One client serves the whole process; create it at start-up and close it on shutdown.",
  steps: [
    {
      title: "Add the dependency",
      body: "Published to Maven Central with every release. Requires Java 11 or later.",
      snippets: [
        { label: "Gradle (Kotlin)", lang: "kotlin", code: `dependencies {\n    implementation("io.github.keshavop:feature-flags-server-jvm:<version>")\n}` },
        {
          label: "Maven",
          lang: "xml",
          code: `<dependency>\n  <groupId>io.github.keshavop</groupId>\n  <artifactId>feature-flags-server-jvm</artifactId>\n  <version><!-- version --></version>\n</dependency>`,
        },
      ],
    },
    {
      title: "Create one client per process",
      body: "Point it at your flag delivery service. The SDK has no built-in service URL; the contract it needs is in HLD §7.",
      snippets: [
        {
          label: "Kotlin",
          lang: "kotlin",
          code: `val client = FeatureFlagServer.create(\n    System.getenv("FF_SERVER_KEY"),\n    ServerConfig(endpoints = ServerEndpoints("https://flags.example.com")),\n)`,
        },
        {
          label: "Java",
          lang: "java",
          code: `FeatureFlagServerClient client = FeatureFlagServer.create(\n    System.getenv("FF_SERVER_KEY"),\n    ServerConfig.builder()\n        .endpoints(new ServerEndpoints("https://flags.example.com"))\n        .build());`,
        },
      ],
    },
    {
      title: "Evaluate for a user",
      body: "Build a context per request. Variation calls never throw: on any problem they return your default and say why in the detail.",
      snippets: [
        {
          label: "Kotlin",
          lang: "kotlin",
          code: `val user = EvaluationContext.user("user-42").set("country", "IN").build()\n\nval enabled = client.boolVariation("new-checkout", user, false)\nval detail = client.boolVariationDetail("new-checkout", user, false) // value + variation + reason`,
        },
      ],
    },
    {
      title: "React to changes and record events",
      body: "Listeners fire when a flag's value changes for that context, usually within a second of the change in the flag service.",
      snippets: [
        {
          label: "Kotlin",
          lang: "kotlin",
          code: `client.flagTracker.addFlagValueChangeListener("new-checkout", user) { change ->\n    println("new-checkout is now \${change.newValue}")\n}\nclient.track("checkout-completed", user, null, 49.99)`,
        },
      ],
    },
    {
      title: "Test without a network",
      body: "Swap the data source for an in-memory one and drive flags from your tests, including percentage rollouts.",
      snippets: [
        {
          label: "Kotlin",
          lang: "kotlin",
          code: `val td = TestDataSource()\nval client = FeatureFlagServer.create("test", ServerConfig(dataSource = ServerDataSource.Custom(td)))\n\ntd.update(td.flag("new-checkout").fallthroughPercentage(30.0))`,
        },
      ],
    },
    {
      title: "Add metrics and tracing, then close on shutdown",
      body: "Micrometer reports ff.eval.count and ff.datasource.state. The OpenTelemetry hook from feature-flags-otel adds a span event per evaluation.",
      snippets: [
        {
          label: "Kotlin",
          lang: "kotlin",
          code: `val metrics = MicrometerMetrics(registry)\nval client = FeatureFlagServer.create(\n    key,\n    ServerConfig(\n        endpoints = ServerEndpoints("https://flags.example.com"),\n        hooks = listOf(metrics.hook, OpenTelemetryHook()),\n    ),\n)\nmetrics.bind(client)\n\nRuntime.getRuntime().addShutdownHook(Thread { client.close() })`,
        },
      ],
    },
  ],
};

const ANDROID: Platform = {
  slug: "android",
  name: "Android",
  runtime: "Kotlin, API 21 and later",
  keyType: "Mobile key",
  data: "Evaluated values for one user",
  updates: "Polling in the foreground, optional pings",
  intro:
    "The Android SDK downloads the values for the signed-in user and serves them from memory. Values from the last session are on disk, so the first screen renders with real flags before the network answers.",
  steps: [
    {
      title: "Add the dependency",
      body: "The Android artifact bundles the shared client core and an OkHttp transport.",
      snippets: [{ label: "Gradle (Kotlin)", lang: "kotlin", code: `dependencies {\n    implementation("io.github.keshavop:feature-flags-android:<version>")\n}` }],
    },
    {
      title: "Start the SDK in your Application",
      body: "Initialize once with the user you know at launch. Use an anonymous context if nobody is signed in yet.",
      snippets: [
        {
          label: "Kotlin",
          lang: "kotlin",
          code: `class App : Application() {\n    override fun onCreate() {\n        super.onCreate()\n        FeatureFlags.init(\n            this,\n            "mob-key",\n            EvaluationContext.user(userId).build(),\n            ClientConfig(endpoints = ClientEndpoints("https://flags.example.com")),\n        )\n    }\n}`,
        },
      ],
    },
    {
      title: "Read flags anywhere",
      body: "Reads are in-memory and safe on the main thread.",
      snippets: [{ label: "Kotlin", lang: "kotlin", code: `val flags = FeatureFlags.get()\nif (flags.boolVariation("new-checkout", false)) {\n    showNewCheckout()\n}` }],
    },
    {
      title: "Update the UI when a flag changes",
      body: "Listeners run on the main thread. Polling pauses while the app is in the background and resumes when it returns.",
      snippets: [{ label: "Kotlin", lang: "kotlin", code: `flags.addListener("new-checkout") { change ->\n    checkoutButton.isVisible = flags.boolVariation(change.key, false)\n}` }],
    },
    {
      title: "Switch users on sign-in",
      body: "identify fetches values for the new context; until they arrive the previous values stay in place.",
      snippets: [{ label: "Kotlin", lang: "kotlin", code: `lifecycleScope.launch {\n    flags.identify(EvaluationContext.user(newUserId).build())\n}` }],
    },
  ],
};

const IOS: Platform = {
  slug: "ios",
  name: "iOS",
  runtime: "Swift, iOS 13 and later",
  keyType: "Mobile key",
  data: "Evaluated values for one user",
  updates: "Polling in the foreground, optional pings",
  intro:
    "The iOS SDK ships as an XCFramework through Swift Package Manager. It shares the evaluation core with every other platform, so a flag gives the same answer on iPhone as on your server.",
  steps: [
    {
      title: "Add the package",
      body: "In Xcode choose File › Add Package Dependencies, paste the repository URL and add the FeatureFlags product.",
      snippets: [
        { label: "Xcode", lang: "text", code: `https://github.com/keshavop/feature-flag-SDK` },
        {
          label: "Package.swift",
          lang: "swift",
          code: `dependencies: [\n    .package(url: "https://github.com/keshavop/feature-flag-SDK", from: "<version>"),\n],\ntargets: [\n    .target(name: "App", dependencies: [.product(name: "FeatureFlags", package: "feature-flag-SDK")]),\n]`,
        },
      ],
    },
    {
      title: "Start the client",
      body: "Start once at launch, for example in your App's init.",
      snippets: [
        {
          label: "Swift",
          lang: "swift",
          code: `import FeatureFlags\n\nlet flags = FeatureFlags.shared.start(\n    mobileKey: "mob-key",\n    context: FeatureFlags.shared.userContext(key: userId, attributes: ["plan": "pro"]),\n    baseUri: "https://flags.example.com"\n)`,
        },
      ],
    },
    {
      title: "Read flags",
      body: "Reads come from memory and never block.",
      snippets: [{ label: "Swift", lang: "swift", code: `if flags.boolVariation(key: "new-checkout", default: false) {\n    showNewCheckout()\n}` }],
    },
    {
      title: "Observe changes",
      body: "The handler runs on the main thread. Keep the handle for as long as you want updates.",
      snippets: [{ label: "Swift", lang: "swift", code: `let handle = FeatureFlags.shared.observe(client: flags, key: "new-checkout") { change in\n    checkoutButton.isHidden = !flags.boolVariation(key: change.key, default: false)\n}` }],
    },
    {
      title: "Switch users on sign-in",
      body: "identify is async and returns once values for the new user are loaded or the timeout passes.",
      snippets: [
        {
          label: "Swift",
          lang: "swift",
          code: `let result = try await flags.identify(\n    newContext: FeatureFlags.shared.userContext(key: newUserId, attributes: [:])\n)`,
        },
      ],
    },
  ],
};

const WEB: Platform = {
  slug: "web",
  name: "Web",
  runtime: "Any modern browser, ES modules",
  keyType: "Client key",
  data: "Evaluated values for one user",
  updates: "Polling while the tab is visible, optional pings",
  intro:
    "The Web SDK is an ES module on npm. It caches values in localStorage so returning visitors see the right variant on first paint, and it stops polling while the tab is hidden.",
  steps: [
    {
      title: "Install the package",
      body: "",
      snippets: [
        { label: "npm", lang: "sh", code: `npm install @keshavop/feature-flags` },
        { label: "pnpm", lang: "sh", code: `pnpm add @keshavop/feature-flags` },
      ],
    },
    {
      title: "Initialize for the visitor",
      body: "The promise resolves when fresh values arrive, or after timeoutMs (5 s by default) with cached or bootstrapped values.",
      snippets: [
        {
          label: "JavaScript",
          lang: "js",
          code: `import { initialize } from "@keshavop/feature-flags";\n\nconst flags = await initialize(\n  "cli-key",\n  { kind: "user", key: "user-42", plan: "pro" },\n  { baseUri: "https://flags.example.com" },\n);`,
        },
      ],
    },
    {
      title: "Read and subscribe",
      body: "on returns a function that removes the listener.",
      snippets: [
        {
          label: "JavaScript",
          lang: "js",
          code: `if (flags.boolVariation("new-checkout", false)) renderNewCheckout();\n\nconst stop = flags.on("change:new-checkout", (value, previous) => render(value));`,
        },
      ],
    },
    {
      title: "Switch users and record events",
      body: "",
      snippets: [
        {
          label: "JavaScript",
          lang: "js",
          code: `await flags.identify({ kind: "user", key: "user-43" });\nflags.track("checkout-completed", { total: 49.99 });\nawait flags.flush(); // e.g. before navigating away`,
        },
      ],
    },
  ],
};

const NEXTJS: Platform = {
  slug: "nextjs",
  name: "Next.js",
  runtime: "App Router with a JVM flag backend",
  keyType: "Server key on the backend, client key in the browser",
  data: "Evaluated values, rendered into the page",
  updates: "Polling in the browser after hydration",
  intro:
    "Render flags on the server and hand them to the browser, so the first paint already shows the right variant with no flicker. The server SDK runs on your JVM backend; Next.js fetches the evaluated values from it and passes them to the Web SDK as bootstrap.",
  steps: [
    {
      title: "Expose evaluated flags from your backend",
      body: "allFlagsState evaluates every flag for one context in the client snapshot format and records no analytics events.",
      snippets: [
        {
          label: "Kotlin (Ktor)",
          lang: "kotlin",
          code: `get("/flags/bootstrap") {\n    val user = EvaluationContext.user(call.parameters["user"]!!).build()\n    val state = flagClient.allFlagsState(user)\n    call.respondText(\n        SnapshotCodec.json.encodeToString(EvaluatedSnapshot.serializer(), state),\n        ContentType.Application.Json,\n    )\n}`,
        },
      ],
    },
    {
      title: "Fetch them in a server component",
      body: "Read the signed-in user on the server and fetch their flags while the page renders.",
      snippets: [
        {
          label: "app/layout.tsx",
          lang: "tsx",
          code: `import { FlagsProvider } from "./flags-provider";\n\nexport default async function RootLayout({ children }: { children: React.ReactNode }) {\n  const user = await currentUser();\n  const bootstrap = await fetch(\n    \`\${process.env.FLAGS_BACKEND}/flags/bootstrap?user=\${encodeURIComponent(user.id)}\`,\n    { cache: "no-store" },\n  ).then((r) => r.json());\n\n  return (\n    <html lang="en">\n      <body>\n        <FlagsProvider user={{ kind: "user", key: user.id }} bootstrap={bootstrap}>\n          {children}\n        </FlagsProvider>\n      </body>\n    </html>\n  );\n}`,
        },
      ],
    },
    {
      title: "Start the Web SDK in a client provider",
      body: "Until the client is ready, components read the bootstrapped values, so server and client render the same markup.",
      snippets: [
        {
          label: "app/flags-provider.tsx",
          lang: "tsx",
          code: `"use client";\nimport { createContext, useContext, useEffect, useState } from "react";\nimport { initialize, type WebClient } from "@keshavop/feature-flags";\n\ntype Bootstrap = { flags: Record<string, { value: unknown }> };\nexport const Flags = createContext<{ client?: WebClient; bootstrap: Bootstrap }>({ bootstrap: { flags: {} } });\n\nexport function FlagsProvider({ user, bootstrap, children }: {\n  user: { kind: string; key: string };\n  bootstrap: Bootstrap;\n  children: React.ReactNode;\n}) {\n  const [client, setClient] = useState<WebClient>();\n  useEffect(() => {\n    let c: WebClient | undefined;\n    initialize(process.env.NEXT_PUBLIC_FLAGS_CLIENT_KEY!, user, {\n      baseUri: process.env.NEXT_PUBLIC_FLAGS_URL,\n      bootstrap,\n    }).then((ready) => setClient((c = ready)));\n    return () => c?.close();\n  }, [user.key]);\n  return <Flags.Provider value={{ client, bootstrap }}>{children}</Flags.Provider>;\n}`,
        },
      ],
    },
    {
      title: "Read flags with a hook",
      body: "The hook re-renders when the flag changes in the browser.",
      snippets: [
        {
          label: "app/use-flag.ts",
          lang: "tsx",
          code: `"use client";\nimport { useContext, useEffect, useState } from "react";\nimport { Flags } from "./flags-provider";\n\nexport function useBoolFlag(key: string, fallback: boolean): boolean {\n  const { client, bootstrap } = useContext(Flags);\n  const initial = (bootstrap.flags[key]?.value as boolean | undefined) ?? fallback;\n  const [value, setValue] = useState(initial);\n  useEffect(() => {\n    if (!client) return;\n    setValue(client.boolVariation(key, fallback));\n    return client.on(\`change:\${key}\`, (next: boolean) => setValue(next));\n  }, [client, key, fallback]);\n  return value;\n}`,
        },
        { label: "Usage", lang: "tsx", code: `const newCheckout = useBoolFlag("new-checkout", false);\nreturn newCheckout ? <NewCheckout /> : <Checkout />;` },
      ],
    },
  ],
};

export const PLATFORMS: Platform[] = [SERVER, ANDROID, IOS, WEB, NEXTJS];

export function platformBySlug(slug: string): Platform | undefined {
  return PLATFORMS.find((p) => p.slug === slug);
}
