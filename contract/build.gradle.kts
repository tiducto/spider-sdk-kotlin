plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `maven-publish`
    signing
}

group = "eu.tiducto"
version = "1.0.0-SNAPSHOT"

// Package layout, by ownership:
//   contract/models    — GENERATED from spider-contract's dist/routing-openapi.json (scripts/generate-contract.sh /
//                        the generate-contract workflow) and committed. Don't hand-edit — regenerate.
//                        The script's `rm -rf` targets ONLY this package.
//   contract/stops     — HAND-WRITTEN stop-search wire contract (mirror of the seed-stops.sh index).
//   contract/realtime  — HAND-WRITTEN GTFS-RT wire contract (mirror of the realtime gateway serializer).
// The hand-written packages live outside `models/` on purpose so a regen never wipes them. If these
// surfaces ever move to codegen, delete the hand-written package and let them regenerate into `models/`.
kotlin {
    jvmToolchain(25)

    applyDefaultHierarchyTemplate()

    jvm()

    android {
        namespace = "eu.tiducto.spider.contract"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()

    js(IR) {
        browser()
        nodejs()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // api, not implementation: the models are @Serializable public types, so consumers
                // need the serialization runtime transitively.
                api(libs.kotlinx.serialization.json)
            }
        }
    }
}

publishing {
    // Prefix the artifactId so the published coordinates read as
    // eu.tiducto:spider-sdk-kotlin-contract(-<target>) rather than the bare module name.
    publications.withType<MavenPublication>().configureEach {
        artifactId = "spider-sdk-kotlin-$artifactId"
        pom {
            name.set("Spider SDK for Kotlin — Contract")
            description.set(
                "Wire-contract types (generated and hand-written) for the Spider transit API " +
                    "Kotlin SDK.",
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
                username = System.getenv("GITHUB_ACTOR").orEmpty()
                password = System.getenv("GITHUB_TOKEN").orEmpty()
            }
        }
    }
}

signing {
    val signingKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
