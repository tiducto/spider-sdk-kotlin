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

    // Kept minimal on purpose: no npm publish, no TS-definition generation, no browser-bundle
    // shaping. Those were all for the retired plain-JS/npm consumption path (now
    // spider-sdk-typescript's job) — this target exists only so another Kotlin/JS Gradle module
    // can still depend on this library like any other KMP target.
    js(IR) {
        browser()
        binaries.library()
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
        pom {
            name.set("Spider SDK for Kotlin")
            description.set(
                "Kotlin Multiplatform SDK for the Spider transit API: trip planning, stop search, " +
                    "and live realtime data.",
            )
            url.set("https://docs.tiducto.eu")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("tiducto")
                    name.set("Tiducto")
                }
            }
            scm {
                url.set("https://github.com/tiducto/spider-sdk-kotlin")
                connection.set("scm:git:https://github.com/tiducto/spider-sdk-kotlin.git")
                developerConnection.set("scm:git:ssh://git@github.com/tiducto/spider-sdk-kotlin.git")
            }
        }
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

// KMP funnels every JS binary flavor through one npm package dir (build/js/packages/spider-sdk-client
// /kotlin), so the development and production library chains both read/write it. `./gradlew build`
// schedules both — the dev distribution (pulled in by check) and the prod distribution (pulled in by
// assemble) — and Gradle 9 fails the build because they touch that shared dir with no ordering edge.
// Declare the order Gradle asks for: the production compile-sync runs only after the development
// distribution has consumed the dev output, serialising the two chains through the dir. Unrelated to
// the retired npm publish — this is a structural quirk of the JS library binary itself.
tasks.named("jsProductionLibraryCompileSync") {
    mustRunAfter("jsBrowserDevelopmentLibraryDistribution")
}

