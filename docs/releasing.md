# Releasing

Every release publishes all SDKs with **one version number**:

| Registry | Artifacts |
|---|---|
| Maven Central | `io.github.keshavop:feature-flags-core` (+ `-jvm`, `-js`, `-iosarm64`, `-iossimulatorarm64`, `-iosx64`) |
| npm | `@keshavop/feature-flags` |

## How to release

1. Make sure `main` is green.
2. Tag and push: `git tag v0.1.0 && git push origin v0.1.0`
3. The [Release workflow](../.github/workflows/release.yml) publishes to Maven Central (from macOS, because of the
   Apple targets) and npm. The version is taken from the tag (`v0.1.0` → `0.1.0`).
   Pre-release tags (`v0.2.0-alpha.1`) are published to npm under the `next` dist-tag.

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
