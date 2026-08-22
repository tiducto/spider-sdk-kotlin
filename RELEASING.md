# Releasing

The Spider Kotlin SDK (`:client`) is published to **Maven Central** through the **Sonatype Central
Portal** using the [`com.vanniktech.maven.publish`](https://vanniktech.github.io/gradle-maven-publish-plugin/)
Gradle plugin. Publishing is automated by `.github/workflows/release.yml`.

Coordinates: `eu.tiducto:spider-sdk-kotlin-client:<version>` (KMP — platform artifacts get a
`-jvm` / `-android` / `-iosarm64` / … suffix automatically).

The version is **not** the git tag — it comes from `version.properties`
(`contract.patch`, e.g. `contract=0.1` + `patch=0` → `0.1.0`). The release workflow fails if a
pushed tag doesn't match that computed version.

---

## One-time setup (before the first release)

All four steps must be done once by a maintainer. Nothing publishes until they are.

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

The workflow maps these to the Gradle properties the plugin reads
(`ORG_GRADLE_PROJECT_mavenCentralUsername` / `…Password` /
`ORG_GRADLE_PROJECT_signingInMemoryKey` / `…KeyPassword`). No secret is ever hardcoded.

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

Alternatively, trigger the **release** workflow manually (Actions → *release* → *Run workflow*);
manual dispatch skips the tag check and publishes whatever `version.properties` currently says.

The workflow runs `./gradlew publishAndReleaseToMavenCentral`, which uploads **and** releases the
deployment — artifacts appear on Maven Central within ~15–30 min (search index can lag a few hours).

> First release / inspect-before-release: to upload without releasing, run
> `./gradlew publishToMavenCentral` locally (with the four `ORG_GRADLE_PROJECT_*` env vars set); the
> deployment then waits in the Central Portal UI for you to click **Publish**.

Maven Central is immutable — a released version can never be overwritten or deleted, so bump the
version for any change; republishing an existing version is rejected.
