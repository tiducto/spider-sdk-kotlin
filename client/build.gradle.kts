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
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.collections.immutable)

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
