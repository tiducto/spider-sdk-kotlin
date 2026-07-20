plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `maven-publish`
}

group = "eu.tiducto"
version = "0.1.0"

kotlin {
    jvmToolchain(25)

    applyDefaultHierarchyTemplate()

    jvm()

    android {
        namespace = "eu.tiducto.spider.client"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()

    js(IR) {
        // Vue/npm-consumable library artifact (package.json + .mjs + .d.mts) — mirrors how
        // spider-services' web/shared exposes its KMP module to the Vue dashboard. The JS-only
        // compiler flags live here (not in a shared compilerOptions block) so the JVM/Apple/Wasm
        // compilations never receive them and reject the unknown arguments.
        outputModuleName = "spider-sdk-client"
        // browser() only — matches web/shared. The published artifact is the browser development
        // library distribution; a nodejs() target only adds a parallel jsNode* distribution chain
        // that nothing consumes and that contends for the same build/js/packages working dir.
        browser()
        binaries.library()
        generateTypeScriptDefinitions()
        compilerOptions {
            // ES2015 classes for a modern, tree-shakeable bundle. Exported suspend funcs return a
            // JS Promise by default since Kotlin 2.4. No -Xes-long-as-bigint: the jsMain facade
            // exposes Double, never Long, so nothing changes the internal transport's Long handling.
            target = "es2015"
            freeCompilerArgs.add("-Xes-classes")
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.collections.immutable)

                // implementation, not api: contract types are used only in the internal transport and
                // must not leak into the public surface (PublicApiLeakTest enforces this).
                implementation(project(":contract"))

                api(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)

                implementation(libs.kermit)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.konsist)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        // appleMain covers all ios* and macos* targets (default hierarchy template).
        val appleMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }
}

publishing {
    // Prefix the artifactId so the published coordinates read as
    // eu.tiducto:spider-sdk-kotlin-client(-<target>) rather than the bare module name.
    publications.withType<MavenPublication>().configureEach {
        artifactId = "spider-sdk-kotlin-$artifactId"
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/tiducto/spider-sdk-kotlin")
            credentials {
                // Graceful fallback: a local build without creds still configures;
                // only an actual `publish` needs these set (GITHUB_ACTOR / GITHUB_TOKEN).
                username = System.getenv("GITHUB_ACTOR").orEmpty()
                password = System.getenv("GITHUB_TOKEN").orEmpty()
            }
        }
    }
}

// The Kotlin/JS library distribution is the npm-publishable artifact (mirrors the Maven publish
// above, but for the JS target → GitHub Packages npm registry under the @tiducto scope). This
// post-processes the generated dist so it can be `npm publish`ed and consumed by a Vite app:
//   1. Scope the package name + point publishConfig at GitHub Packages npm.
//   2. Drop sourcemaps — they reference intermediate Kotlin build paths that don't survive, so a
//      consuming Vite build only warns about missing sources (same reasoning as web/shared).
// See .github/workflows/publish-npm.yml for the actual publish (uses the built-in GITHUB_TOKEN).
tasks.named("jsBrowserDevelopmentLibraryDistribution") {
    val distDir = layout.buildDirectory.dir("dist/js/developmentLibrary")
    doLast {
        val dir = distDir.get().asFile
        dir.listFiles()?.forEach { f ->
            when {
                f.name.endsWith(".mjs.map") -> f.delete()
                f.name.endsWith(".mjs") -> {
                    val stripped = f.readText().replace(Regex("\n//# sourceMappingURL=\\S+\\.map\\s*$"), "")
                    f.writeText(stripped)
                }
            }
        }

        val pkg = dir.resolve("package.json")
        pkg.writeText(
            pkg.readText().replace(
                "  \"name\": \"spider-sdk-client\",",
                """
                |  "name": "@tiducto/spider-sdk-client",
                |  "description": "Spider transit API SDK for JS/TS — trip planning, stop search and realtime.",
                |  "repository": {
                |    "type": "git",
                |    "url": "git+https://github.com/tiducto/spider-sdk-kotlin.git"
                |  },
                |  "license": "UNLICENSED",
                |  "publishConfig": {
                |    "registry": "https://npm.pkg.github.com"
                |  },
                """.trimMargin(),
            ),
        )
    }
}

// Golden snapshot of the exported TypeScript surface (the @JsExport jsMain facade). checkJsApi fails
// if the generated .d.mts drifts from the committed golden, so any facade change lands as a reviewable
// diff; run :client:updateJsApi to accept it. Complements checkKotlinAbi (which snapshots the Kotlin
// ABI): together, a core change and a facade change are each forced into a committed diff, so the
// facade can't silently fall out of parity with the commonMain client. Type/surface only — no runtime
// response data is parsed or fetched.
val generatedJsDts = layout.buildDirectory.file("dist/js/developmentLibrary/spider-sdk-client.d.mts")
val jsApiGolden = layout.projectDirectory.file("api/spider-sdk-client.d.mts")

tasks.register("updateJsApi") {
    dependsOn("jsBrowserDevelopmentLibraryDistribution")
    doLast {
        jsApiGolden.asFile.parentFile.mkdirs()
        generatedJsDts.get().asFile.copyTo(jsApiGolden.asFile, overwrite = true)
    }
}

val checkJsApi = tasks.register("checkJsApi") {
    dependsOn("jsBrowserDevelopmentLibraryDistribution")
    doLast {
        val generated = generatedJsDts.get().asFile
        val golden = jsApiGolden.asFile
        if (!golden.exists() || generated.readText() != golden.readText()) {
            throw GradleException(
                "JS API drift: generated spider-sdk-client.d.mts differs from api/spider-sdk-client.d.mts. " +
                    "Review the change, then run ':client:updateJsApi' to accept it.",
            )
        }
    }
}

tasks.named("check") { dependsOn(checkJsApi) }

// KMP funnels every JS binary flavor through one npm package dir (build/js/packages/spider-sdk-client
// /kotlin), so the development and production library chains both read/write it. `./gradlew build`
// schedules both — the dev distribution (pulled in by checkJsApi above) and the prod distribution
// (pulled in by assemble) — and Gradle 9 fails the build because they touch that shared dir with no
// ordering edge. Declare the order Gradle asks for: the production compile-sync runs only after the
// development distribution has consumed the dev output, serialising the two chains through the dir.
tasks.named("jsProductionLibraryCompileSync") {
    mustRunAfter("jsBrowserDevelopmentLibraryDistribution")
}
