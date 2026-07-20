plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `maven-publish`
}

group = "eu.tiducto"
version = "1.0.0-SNAPSHOT"

kotlin {
    jvmToolchain(25)

    applyDefaultHierarchyTemplate()

    jvm()

    android {
        namespace = "cz.davidkurzica.client"
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
        browser()
        nodejs()
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
