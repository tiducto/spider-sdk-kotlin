# Releasing

The Spider Kotlin SDK (`:client`) is published to two Maven repositories from one build:

- **the mirror**, our own repository on GitHub Pages (`gh-pages` branch, served at
  <https://tiducto.github.io/spider-sdk-kotlin/>), which every release reaches first;
- **Maven Central**, through the **Sonatype Central Portal**, from exactly the files on the mirror.

The [`com.vanniktech.maven.publish`](https://vanniktech.github.io/gradle-maven-publish-plugin/)
Gradle plugin configures the publications (POM, sources/javadoc jars, signing); Gradle's own
`maven-publish` writes them into the mirror, and the Central Portal Publisher API uploads them.
Publishing is automated by `.github/workflows/release.yml`; snapshots of `main` by
`.github/workflows/snapshot.yml`.

Coordinates: `eu.tiducto:spider-sdk-kotlin-client:<version>` (KMP — platform artifacts get a
`-jvm` / `-android` / `-iosarm64` / … suffix automatically).

## Release cadence and the Central quota

The Central Portal caps the `tiducto` organization at **7 releases and 1,167 files a month**, and a
Kotlin release is 224 files (10 publications × artifact, `.asc`, `.md5`, `.sha1`). So the Kotlin
SDK releases to Maven Central **at most once a week**. Between releases, `main` is always available
as a snapshot (below), and the mirror carries every release even when Central refuses it.

The version is **not** the git tag — it comes from `version.properties`
(`contract.patch`, e.g. `contract=0.1` + `patch=0` → `0.1.0`). The release workflow fails if a
pushed tag doesn't match that computed version.

---

## One-time setup (before the first release)

All five steps must be done once by a maintainer (all are done for this repository). Nothing
publishes until they are.

### 1. Sonatype Central Portal account

Sign up at <https://central.sonatype.com/> (GitHub or email login).

### 2. Verify the `eu.tiducto` namespace

In the Central Portal, add a namespace `eu.tiducto`. Because we own the `tiducto.eu` domain, verify
it via DNS: the Portal shows a **verification key**; add it as a **TXT record** on `tiducto.eu`, e.g.

```
tiducto.eu.  TXT  "<verification-key-from-portal>"
```

(Cloudflare → tiducto.eu zone → DNS → add TXT.) Click **Verify** in the Portal once the record
propagates. This proves ownership of the reverse-domain group id and is required for the first
publish only.

### 3. GPG signing key

Central requires every artifact to be GPG-signed. Generate a key and publish its **public** half to
a keyserver so Central can verify signatures:

```bash
# Generate (choose RSA 4096; set a passphrase and remember it)
gpg --gen-key                # or: gpg --full-generate-key

# Find the key id (the long hex under "sec")
gpg --list-secret-keys --keyid-format=long

# Publish the PUBLIC key so Central can verify it
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>

# Export the ASCII-armored PRIVATE key — this whole block is the GPG_SIGNING_KEY secret
gpg --armor --export-secret-keys <KEY_ID>
```

The `signingInMemoryKey` the plugin expects is the full ASCII-armored private key, including the
`-----BEGIN/END PGP PRIVATE KEY BLOCK-----` lines.

### 4. Repository secrets

Add these four **GitHub Actions secrets** (repo → Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `SONATYPE_CENTRAL_TOKEN_USER` | Central Portal **user token** username (Portal → Account → *Generate User Token*) — **not** your login) |
| `SONATYPE_CENTRAL_TOKEN_PASSWORD` | Central Portal user token password (the token's secret half) |
| `GPG_SIGNING_KEY` | The ASCII-armored GPG **private** key from step 3 |
| `GPG_SIGNING_PASSWORD` | The passphrase for that GPG key |

The workflows map the GPG secrets to the Gradle properties the plugin signs with
(`ORG_GRADLE_PROJECT_signingInMemoryKey` / `…KeyPassword`) and hand the token pair to
`.github/scripts/central-upload.sh`, which sends it as `Authorization: Bearer base64(user:password)`.
No secret is ever hardcoded.

### 5. The mirror (GitHub Pages)

The `gh-pages` branch holds the Maven repository: `.nojekyll`, an `index.html`, releases at the
root and snapshots under `snapshots/`. Pages serves it from `gh-pages` at `/`
(repo → Settings → Pages). The workflows push to it with `GITHUB_TOKEN` (`contents: write` on the
publishing jobs only); no extra secret is needed.

---

## Cutting a release

1. Set the version in `version.properties` (`contract` + `patch`). The `contract` major.minor
   mirrors the Spider API contract; bump `patch` for SDK-only changes. Update `CHANGELOG.md`.
2. Commit to `main`.
3. Push a tag equal to the resulting version (no `v` prefix):

   ```bash
   git tag 0.1.0
   git push origin 0.1.0
   ```

   The `release` workflow runs on the tag, verifies it matches `version.properties`, and publishes.

The pipeline:

1. **build** — `build.yml` on the ref being released.
2. **mirror** — builds and signs `:client` once and publishes it with Gradle into the `gh-pages`
   tree (`publishAllPublicationsToMirrorRepository`, a `mirror` repository added by
   `.github/mirror-repo.init.gradle.kts`, so it works on tags that predate this pipeline). Gradle
   merges each artifact's `maven-metadata.xml`. A version already on the mirror is never rebuilt or
   overwritten; its files are reused. The job also drops the snapshots of the released version and
   pushes the tree as one orphan commit.
3. **central** — `central-upload.yml`: `.github/scripts/central-bundle.sh` zips exactly that
   version's files from `gh-pages` (artifacts, `.asc`, `.md5`, `.sha1`; no `sha256`/`sha512`, no
   checksums of `.asc`, no `maven-metadata.xml`), checks every artifact has its signature and
   checksums, and `.github/scripts/central-upload.sh` uploads it to
   `POST /api/v1/publisher/upload?publishingType=AUTOMATIC`, then polls
   `POST /api/v1/publisher/status` until `PUBLISHED` (or fails on `FAILED`, printing the errors).
   A version already on Maven Central is skipped. Artifacts appear on Maven Central within ~15–30 min
   (search index can lag a few hours).
4. **refresh-context** — re-indexes Context7 once the mirror has the release; it does not wait on
   Central.

A Central failure never touches the mirror. To retry the upload later, from the files on the
mirror and without rebuilding:

```bash
gh workflow run central-upload.yml --repo tiducto/spider-sdk-kotlin --ref main -f version=<version>
```

Manual dispatch of **release** (Actions → *Release* → *Run workflow*) takes `ref`, the tag (or
branch) to build — the pipeline itself comes from the branch you dispatch on, so an older tag
publishes with today's pipeline — and `central`, off to publish to the mirror only. A tag `ref` must
match its `version.properties`; a branch publishes whatever `version.properties` says.

Every `gh-pages` writer (release mirror, snapshots) is in the `gh-pages` concurrency group, so they
never race. GitHub keeps one pending job per group: if a third writer queues while one runs and
one waits, the waiting one is cancelled. Rerun it; both are idempotent.

### Publishing 1.10.0 (tagged before the mirror existed)

1.10.0 was refused by Central (monthly file limit, resets 2026-12-01). Once this pipeline is on
`main`, publish it to the mirror now:

```bash
gh workflow run release.yml --repo tiducto/spider-sdk-kotlin --ref main -f ref=1.10.0 -f central=false
```

Then, on or after 2026-12-01, upload the same files to Maven Central:

```bash
gh workflow run central-upload.yml --repo tiducto/spider-sdk-kotlin --ref main -f version=1.10.0
```

Maven Central and the mirror are immutable — a released version can never be overwritten or
deleted, so bump the version for any change.

## Snapshots

Every push to `main` (and a manual dispatch of **Snapshot** on `main`) publishes a snapshot to
<https://tiducto.github.io/spider-sdk-kotlin/snapshots/>. The version is `version.properties`'
`<contract>.<patch>-SNAPSHOT` while that version is unreleased, else the next patch (`1.10.0`
released → `1.10.1-SNAPSHOT`); it is passed as `-PsdkVersion` and nothing is committed to `main`.
A version is released once it is tagged or on the mirror: a release published to the mirror with
`central` off and no tag yet (Central later, by pushing its tag) counts too.
`.github/scripts/snapshot-prune.sh` deletes that version's previous build before Gradle publishes
it, so each snapshot version keeps only its newest build, and deletes every snapshot version whose
release is out, rewriting the artifact's `maven-metadata.xml` (or removing the artifact when
nothing is left). A newer push cancels a running snapshot build. Snapshots never go to Maven Central.

The `gh-pages` tree is always pushed as a single orphan commit, so deleted snapshots do not pile up
in the branch history.
