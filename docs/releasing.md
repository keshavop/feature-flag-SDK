# Releasing

Every release publishes all SDKs with **one version number**:

| Registry | Artifacts |
|---|---|
| Maven Central | `io.github.keshavop:feature-flags-{core,sync,events,client-common,transport-okhttp,server-jvm,android,ios}` (KMP modules with per-target artifacts) |
| npm | `@keshavop/feature-flags` |
| Swift Package Manager | `https://github.com/keshavop/feature-flag-SDK` (binary `FeatureFlags.xcframework` attached to the GitHub release) |

## How to release

1. Make sure `main` is green.
2. Actions → **Release** → *Run workflow* on `main`, enter the version (e.g. `0.1.0`).
3. The [workflow](../.github/workflows/release.yml):
   1. builds `FeatureFlags.xcframework` on macOS, computes its SPM checksum, commits `Package.swift` with the version and
      checksum (`chore(release): v0.1.0`), tags `v0.1.0` and creates the GitHub release with the zip attached;
   2. publishes Maven Central (from macOS, because of the Apple targets) and npm from that tag.

   Pre-releases (`0.2.0-alpha.1`) are marked as GitHub pre-releases and go to npm's `next` dist-tag.

The workflow pushes the release commit to `main` with the default `GITHUB_TOKEN`; if `main` is protected, allow
GitHub Actions to bypass the rule or run the workflow from a release branch.

## One-time setup

### Maven Central
1. Sign in at [central.sonatype.com](https://central.sonatype.com) with the `keshavop` GitHub account and verify the
   `io.github.keshavop` namespace (automatic for GitHub logins).
2. Generate a user token (*Account → Generate User Token*).
3. Create a GPG signing key and publish it to a keyserver:
   ```sh
   gpg --full-generate-key
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   gpg --export-secret-keys --armor <KEY_ID>   # value for SIGNING_KEY
   ```

### npm
1. Sign in to [npmjs.com](https://www.npmjs.com) as `keshavop` (the `@keshavop` scope is your user scope). If the npm
   account has a different name, create an npm organization called `keshavop` or change `npmPackageName` in
   `ff-web/build.gradle.kts`.
2. Create a granular access token with publish rights for `@keshavop/feature-flags`.

### GitHub repository secrets

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Central Portal user-token username |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user-token password |
| `SIGNING_KEY` | ASCII-armored GPG private key |
| `SIGNING_KEY_PASSWORD` | GPG key passphrase |
| `NPM_TOKEN` | npm access token |

## Local dry run

```sh
./gradlew publishToMavenLocal                 # unsigned artifacts in ~/.m2
./gradlew :ff-web:prepareNpmPackage           # npm package in ff-web/build/npm
(cd ff-web/build/npm && npm pack --dry-run)
```
